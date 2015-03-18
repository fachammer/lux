(ns lux.host
  (:require (clojure [string :as string]
                     [template :refer [do-template]])
            [clojure.core.match :as M :refer [match matchv]]
            clojure.core.match.array
            (lux [base :as & :refer [exec return* return fail fail*
                                     repeat-m try-all-m map-m mapcat-m reduce-m
                                     normalize-ident]]
                 [parser :as &parser]
                 [type :as &type])))

;; [Constants]
(def prefix "lux.")
(def function-class (str prefix "Function"))

;; [Utils]
(defn ^:private class->type [class]
  (if-let [[_ base arr-level] (re-find #"^([^\[]+)(\[\])*$"
                                       (str (if-let [pkg (.getPackage class)]
                                              (str (.getName pkg) ".")
                                              "")
                                            (.getSimpleName class)))]
    (if (= "void" base)
      (return (&/V "Nothing" nil))
      (let [base* (&/V "Data" (to-array [base (&/V "Nil" nil)]))]
        (if arr-level
          (return (reduce (fn [inner _]
                            (&/V "array" (&/V "Cons" (to-array [inner (&/V "Nil" nil)]))))
                          base*
                          (range (/ (count arr-level) 2.0))))
          (return base*)))
      )))

(defn ^:private method->type [method]
  (exec [=args (map-m class->type (seq (.getParameterTypes method)))
         =return (class->type (.getReturnType method))]
    (return [=args =return])))

;; [Resources]
(defn full-class [class-name]
  (case class
    "boolean" (return Boolean/TYPE)
    "byte"    (return Byte/TYPE)
    "short"   (return Short/TYPE)
    "int"     (return Integer/TYPE)
    "long"    (return Long/TYPE)
    "float"   (return Float/TYPE)
    "double"  (return Double/TYPE)
    "char"    (return Character/TYPE)
    ;; else
    (try (return (Class/forName class-name))
      (catch Exception e
        (fail "[Analyser Error] Unknown class.")))))

(defn full-class-name [class-name]
  (exec [=class (full-class class-name)]
    (return (.getName =class))))

(defn ->class [class]
  (string/replace class #"\." "/"))

(def ->package ->class)

(defn ->type-signature [class]
  (case class
    "void"    "V"
    "boolean" "Z"
    "byte"    "B"
    "short"   "S"
    "int"     "I"
    "long"    "J"
    "float"   "F"
    "double"  "D"
    "char"    "C"
    ;; else
    (let [class* (->class class)]
      (if (.startsWith class* "[")
        class*
        (str "L" class* ";")))
    ))

(defn ->java-sig [type]
  (matchv ::M/objects [type]
    [["Any" _]]
    (->type-signature "java.lang.Object")

    [["Nothing" _]]
    "V"
    
    [["Data" ["array" ["Cons" [?elem ["Nil" _]]]]]]
    (str "[" (->java-sig ?elem))

    [["Data" [?name ?params]]]
    (->type-signature ?name)

    [["Lambda" [_ _]]]
    (->type-signature function-class)))

(defn extract-jvm-param [token]
  (matchv ::M/objects [token]
    [["Ident" ?ident]]
    (full-class-name ?ident)

    [["Form" ["Cons" [["Ident" "Array"] ["Cons" [["Ident" ?inner] ["Nil" _]]]]]]]
    (exec [=inner (full-class-name ?inner)]
      (return (str "[L" (->class =inner) ";")))

    [_]
    (fail (str "[Host] Unknown JVM param: " (pr-str token)))))

(do-template [<name> <static?>]
  (defn <name> [target field]
    (let [target (Class/forName target)]
      (if-let [type* (first (for [=field (.getFields target)
                                  :when (and (= target (.getDeclaringClass =field))
                                             (= field (.getName =field))
                                             (= <static?> (java.lang.reflect.Modifier/isStatic (.getModifiers =field))))]
                              (.getType =field)))]
        (exec [=type (class->type type*)]
          (return =type))
        (fail (str "[Analyser Error] Field does not exist: " target field)))))

  lookup-static-field true
  lookup-field        false
  )

(do-template [<name> <static?>]
  (defn <name> [target method-name args]
    (let [target (Class/forName target)]
      (if-let [method (first (for [=method (.getMethods target)
                                   ;; :let [_ (prn '<name> '=method =method (mapv #(.getName %) (.getParameterTypes =method)))]
                                   :when (and (= target (.getDeclaringClass =method))
                                              (= method-name (.getName =method))
                                              (= <static?> (java.lang.reflect.Modifier/isStatic (.getModifiers =method)))
                                              (= args (mapv #(.getName %) (.getParameterTypes =method))))]
                               =method))]
        (exec [=method (method->type method)]
          (return =method))
        (fail (str "[Analyser Error] Method does not exist: " target method-name)))))

  lookup-static-method  true
  lookup-virtual-method false
  )

(defn location [scope]
  (->> scope (map normalize-ident) (interpose "$") (reduce str "")))