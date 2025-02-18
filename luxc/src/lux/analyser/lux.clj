(ns lux.analyser.lux
  (:require (clojure [template :refer [do-template]]
                     [set :as set])
            clojure.core.match
            clojure.core.match.array
            (lux [base :as & :refer [|do return return* |let |list |case]]
                 [parser :as &parser]
                 [type :as &type]
                 [host :as &host])
            (lux.analyser [base :as &&]
                          [function :as &&function]
                          [case :as &&case]
                          [env :as &&env]
                          [module :as &&module]
                          [record :as &&record])))

;; [Utils]
;; TODO: Walk the type to set up the parameter-type, instead of doing a
;; rough calculation like this one.
(defn ^:private count-univq
  "(-> Type Int)"
  [type]
  (|case type
    (&/$UnivQ env type*)
    (inc (count-univq type*))

    _
    0))

;; TODO: This technique will not work if the body of the type contains
;; nested quantifications that cannot be directly counted.
(defn ^:private next-parameter-type
  "(-> Type Type)"
  [type]
  (&/$Parameter (->> (count-univq type) (* 2) (+ 1))))

(defn ^:private embed-inferred-input
  "(-> Type Type Type)"
  [input output]
  (|case output
    (&/$UnivQ env output*)
    (&/$UnivQ env (embed-inferred-input input output*))

    _
    (&/$Function input output)))

;; [Exports]
(defn analyse-unit [analyse ?exo-type]
  (|do [_cursor &/cursor
        _ (&type/check ?exo-type &type/Any)]
    (return (&/|list (&&/|meta ?exo-type _cursor
                               (&&/$tuple (&/|list)))))))

(defn analyse-tuple [analyse ?exo-type ?elems]
  (|case ?elems
    (&/$Nil)
    (analyse-unit analyse (|case ?exo-type
                            (&/$Left exo-type) exo-type
                            (&/$Right exo-type) exo-type))

    (&/$Cons ?elem (&/$Nil))
    (analyse (|case ?exo-type
               (&/$Left exo-type) exo-type
               (&/$Right exo-type) exo-type)
             ?elem)

    _
    (|case ?exo-type
      (&/$Left exo-type)
      (|do [exo-type* (&type/actual-type exo-type)]
        (|case exo-type*
          (&/$UnivQ _)
          (&type/with-var
            (fn [$var]
              (|do [exo-type** (&type/apply-type exo-type* $var)
                    [[tuple-type tuple-cursor] tuple-analysis] (&&/cap-1 (analyse-tuple analyse (&/$Left exo-type**) ?elems))
                    =var (&type/resolve-type $var)
                    inferred-type (|case =var
                                    (&/$Var iid)
                                    (|do [:let [=var* (next-parameter-type tuple-type)]
                                          _ (&type/set-var iid =var*)
                                          tuple-type* (&type/clean $var tuple-type)]
                                      (return (&/$UnivQ &/$Nil tuple-type*)))

                                    _
                                    (&type/clean $var tuple-type))]
                (return (&/|list (&&/|meta inferred-type tuple-cursor
                                           tuple-analysis))))))

          _
          (analyse-tuple analyse (&/$Right exo-type*) ?elems)))

      (&/$Right exo-type)
      (|do [unknown? (&type/unknown? exo-type)]
        (if unknown?
          (|do [=elems (&/map% #(|do [=analysis (&&/analyse-1+ analyse %)]
                                  (return =analysis))
                               ?elems)
                _ (&type/check exo-type (|case (->> (&/|map &&/expr-type* =elems) (&/|reverse))
                                          (&/$Cons last prevs)
                                          (&/fold (fn [right left] (&/$Product left right))
                                                  last prevs)))
                _cursor &/cursor]
            (return (&/|list (&&/|meta exo-type _cursor
                                       (&&/$tuple =elems)
                                       ))))
          (|do [exo-type* (&type/actual-type exo-type)]
            (&/with-attempt
              (|case exo-type*
                (&/$Product _)
                (|let [num-elems (&/|length ?elems)
                       [_shorter _tuple-types] (&type/tuple-types-for num-elems exo-type*)]
                  (if (= num-elems _shorter)
                    (|do [=elems (&/map2% (fn [elem-t elem]
                                            (&&/analyse-1 analyse elem-t elem))
                                          _tuple-types
                                          ?elems)
                          _cursor &/cursor]
                      (return (&/|list (&&/|meta exo-type _cursor
                                                 (&&/$tuple =elems)
                                                 ))))
                    (|do [=direct-elems (&/map2% (fn [elem-t elem] (&&/analyse-1 analyse elem-t elem))
                                                 (&/|take (dec _shorter) _tuple-types)
                                                 (&/|take (dec _shorter) ?elems))
                          =indirect-elems (analyse-tuple analyse
                                                         (&/$Right (&/|last _tuple-types))
                                                         (&/|drop (dec _shorter) ?elems))
                          _cursor &/cursor]
                      (return (&/|list (&&/|meta exo-type _cursor
                                                 (&&/$tuple (&/|++ =direct-elems =indirect-elems))
                                                 ))))))

                (&/$ExQ _)
                (&type/with-var
                  (fn [$var]
                    (|do [exo-type** (&type/apply-type exo-type* $var)
                          [[tuple-type tuple-cursor] tuple-analysis] (&&/cap-1 (analyse-tuple analyse (&/$Right exo-type**) ?elems))
                          =tuple-analysis (&&/clean-analysis $var (&&/|meta exo-type tuple-cursor
                                                                            tuple-analysis))]
                      (return (&/|list =tuple-analysis)))))

                (&/$UnivQ _)
                (|do [$var &type/existential
                      :let [(&/$Ex $var-id) $var]
                      exo-type** (&type/apply-type exo-type* $var)
                      [[tuple-type tuple-cursor] tuple-analysis] (&/with-scope-type-var $var-id
                                                                   (&&/cap-1 (analyse-tuple analyse (&/$Right exo-type**) ?elems)))]
                  (return (&/|list (&&/|meta exo-type tuple-cursor
                                             tuple-analysis))))

                _
                (&/fail-with-loc (str "[Analyser Error] Tuples require tuple-types: " (&type/show-type exo-type*)))
                )
              (fn [err]
                (&/fail-with-loc (str err "\n" "[Analyser Error] Tuples require tuple-types: " (&type/show-type exo-type)))))))))
    ))

(defn ^:private analyse-variant-body [analyse exo-type ?values]
  (|do [_cursor &/cursor
        output (|case ?values
                 (&/$Nil)
                 (analyse-unit analyse exo-type)

                 (&/$Cons ?value (&/$Nil))
                 (analyse exo-type ?value)

                 _
                 (analyse-tuple analyse (&/$Right exo-type) ?values))]
    (|case output
      (&/$Cons x (&/$Nil))
      (return x)

      _
      (&/fail-with-loc "[Analyser Error] Macro cannot expand to more than 1 output."))))

(defn analyse-variant [analyse ?exo-type idx is-last? ?values]
  (|case ?exo-type
    (&/$Left exo-type)
    (|do [exo-type* (&type/actual-type exo-type)]
      (|case exo-type*
        (&/$UnivQ _)
        (&type/with-var
          (fn [$var]
            (|do [exo-type** (&type/apply-type exo-type* $var)
                  [[variant-type variant-cursor] variant-analysis] (&&/cap-1 (analyse-variant analyse (&/$Left exo-type**) idx is-last? ?values))
                  =var (&type/resolve-type $var)
                  inferred-type (|case =var
                                  (&/$Var iid)
                                  (|do [:let [=var* (next-parameter-type variant-type)]
                                        _ (&type/set-var iid =var*)
                                        variant-type* (&type/clean $var variant-type)]
                                    (return (&/$UnivQ &/$Nil variant-type*)))

                                  _
                                  (&type/clean $var variant-type))]
              (return (&/|list (&&/|meta inferred-type variant-cursor
                                         variant-analysis))))))

        _
        (analyse-variant analyse (&/$Right exo-type*) idx is-last? ?values)))

    (&/$Right exo-type)
    (|do [exo-type* (|case exo-type
                      (&/$Var ?id)
                      (&/try-all% (&/|list (|do [exo-type* (&type/deref ?id)]
                                             (&type/actual-type exo-type*))
                                           (|do [_ (&type/set-var ?id &type/Type)]
                                             (&type/actual-type &type/Type))))

                      _
                      (&type/actual-type exo-type))]
      (&/with-attempt
        (|case exo-type*
          (&/$Sum _)
          (|do [vtype (&type/sum-at idx exo-type*)
                :let [num-variant-types (&/|length (&type/flatten-sum exo-type*))
                      is-last?* (if (nil? is-last?)
                                  (= idx (dec num-variant-types))
                                  is-last?)]
                =value (analyse-variant-body analyse vtype ?values)
                _cursor &/cursor]
            (if (= 1 num-variant-types)
              (return (&/|list =value))
              (return (&/|list (&&/|meta exo-type _cursor (&&/$variant idx is-last?* =value))))
              ))

          (&/$UnivQ _)
          (|do [$var &type/existential
                exo-type** (&type/apply-type exo-type* $var)]
            (analyse-variant analyse (&/$Right exo-type**) idx is-last? ?values))

          (&/$ExQ _)
          (&type/with-var
            (fn [$var]
              (|do [exo-type** (&type/apply-type exo-type* $var)
                    =exprs (analyse-variant analyse (&/$Right exo-type**) idx is-last? ?values)]
                (&/map% (partial &&/clean-analysis $var) =exprs))))
          
          _
          (&/fail-with-loc (str "[Analyser Error] Cannot create variant if the expected type is " (&type/show-type exo-type*) " " idx " " (->> ?values (&/|map &/show-ast) (&/|interpose " ") (&/fold str "")))))
        (fn [err]
          (|case exo-type
            (&/$Var ?id)
            (|do [=exo-type (&type/deref ?id)]
              (&/fail-with-loc (str err "\n" "[Analyser Error] Cannot create variant if the expected type is " (&type/show-type =exo-type) " " idx " " (->> ?values (&/|map &/show-ast) (&/|interpose " ") (&/fold str "")))))

            _
            (&/fail-with-loc (str err "\n" "[Analyser Error] Cannot create variant if the expected type is " (&type/show-type exo-type) " " idx " " (->> ?values (&/|map &/show-ast) (&/|interpose " ") (&/fold str "")))))))
      )))

(defn analyse-record [analyse exo-type ?elems]
  (|do [[rec-members rec-type] (&&record/order-record ?elems)]
    (|case exo-type
      (&/$Var id)
      (|do [? (&type/bound? id)]
        (if ?
          (analyse-tuple analyse (&/$Right exo-type) rec-members)
          (|do [[[tuple-type tuple-cursor] tuple-analysis] (&&/cap-1 (analyse-tuple analyse (&/$Left rec-type) rec-members))
                _ (&type/check exo-type tuple-type)]
            (return (&/|list (&&/|meta exo-type tuple-cursor
                                       tuple-analysis))))))

      _
      (analyse-tuple analyse (&/$Right exo-type) rec-members)
      )))

(defn ^:private analyse-global [analyse exo-type module name]
  (|do [[[r-module r-name] [exported? endo-type ?meta ?value]] (&&module/find-def module name)
        ;; This is a small shortcut to optimize analysis of typing code.
        _ (if (and (&type/type= &type/Type endo-type)
                   (&type/type= &type/Type exo-type))
            (return nil)
            (&type/check exo-type endo-type))
        _cursor &/cursor]
    (return (&/|list (&&/|meta endo-type _cursor
                               (&&/$def (&/T [r-module r-name])))))))

(defn ^:private analyse-local [analyse exo-type name]
  (fn [state]
    (|let [stack (&/get$ &/$scopes state)
           no-binding? #(and (->> % (&/get$ &/$locals)  (&/get$ &/$mappings) (&/|contains? name) not)
                             (->> % (&/get$ &/$captured) (&/get$ &/$mappings) (&/|contains? name) not))
           [inner outer] (&/|split-with no-binding? stack)]
      (|case outer
        (&/$Nil)
        (&/run-state (|do [module-name &/get-module-name]
                       (analyse-global analyse exo-type module-name name))
                     state)

        (&/$Cons bottom-outer _)
        (|let [scopes (&/|map #(&/get$ &/$name %) (&/|reverse inner))
               [=local inner*] (&/fold2 (fn [register+new-inner frame in-scope]
                                          (|let [[register new-inner] register+new-inner
                                                 [register* frame*] (&&function/close-over in-scope name register frame)]
                                            (&/T [register* (&/$Cons frame* new-inner)])))
                                        (&/T [(&/|second (or (->> bottom-outer (&/get$ &/$locals)  (&/get$ &/$mappings) (&/|get name))
                                                             (->> bottom-outer (&/get$ &/$captured) (&/get$ &/$mappings) (&/|get name))))
                                              &/$Nil])
                                        (&/|reverse inner) scopes)]
          ((|do [_ (&type/check exo-type (&&/expr-type* =local))]
             (return (&/|list =local)))
           (&/set$ &/$scopes (&/|++ inner* outer) state)))
        ))))

(defn analyse-identifier [analyse exo-type ident]
  (|do [:let [[?module ?name] ident]]
    (if (= "" ?module)
      (analyse-local analyse exo-type ?name)
      (analyse-global analyse exo-type ?module ?name))
    ))

(defn ^:private analyse-apply* [analyse exo-type fun-type ?args]
  (|case ?args
    (&/$Nil)
    (|do [_ (&type/check exo-type fun-type)]
      (return (&/T [fun-type &/$Nil])))
    
    (&/$Cons ?arg ?args*)
    (|do [?fun-type* (&type/actual-type fun-type)]
      (&/with-attempt
        (|case ?fun-type*
          (&/$UnivQ _)
          (&type/with-var
            (fn [$var]
              (|do [type* (&type/apply-type ?fun-type* $var)
                    [=output-t =args] (analyse-apply* analyse exo-type type* ?args)
                    ==args (&/map% (partial &&/clean-analysis $var) =args)]
                (|case $var
                  (&/$Var ?id)
                  (|do [? (&type/bound? ?id)
                        type** (if ?
                                 (&type/clean $var =output-t)
                                 (|do [_ (&type/set-var ?id (next-parameter-type =output-t))
                                       cleaned-output* (&type/clean $var =output-t)
                                       :let [cleaned-output (&/$UnivQ &/$Nil cleaned-output*)]]
                                   (return cleaned-output)))
                        _ (&type/clean $var exo-type)]
                    (return (&/T [type** ==args])))
                  ))))

          (&/$ExQ _)
          (&type/with-var
            (fn [$var]
              (|do [type* (&type/apply-type ?fun-type* $var)
                    [=output-t =args] (analyse-apply* analyse exo-type type* ?args)
                    ==args (&/map% (partial &&/clean-analysis $var) =args)]
                (|case $var
                  (&/$Var ?id)
                  (|do [? (&type/bound? ?id)
                        type** (if ?
                                 (&type/clean $var =output-t)
                                 (|do [idT &type/existential
                                       _ (&type/set-var ?id idT)]
                                   (&type/clean $var =output-t)))
                        _ (&type/clean $var exo-type)]
                    (return (&/T [type** ==args])))
                  ))))

          (&/$Function ?input-t ?output-t)
          (|do [[=output-t =args] (analyse-apply* analyse exo-type ?output-t ?args*)
                =arg (&/with-attempt
                       (&&/analyse-1 analyse ?input-t ?arg)
                       (fn [err]
                         (&/fail-with-loc (str err "\n" "[Analyser Error] Argument expected: " (&type/show-type ?input-t)))))]
            (return (&/T [=output-t (&/$Cons =arg =args)])))

          _
          (&/fail-with-loc (str "[Analyser Error] Cannot apply a non-function: " (&type/show-type ?fun-type*))))
        (fn [err]
          (&/fail-with-loc (str err "\n" "[Analyser Error] Cannot apply function " (&type/show-type fun-type) " to args: " (->> ?args (&/|map &/show-ast) (&/|interpose " ") (&/fold str "")))))))
    ))

(defn ^:private do-analyse-apply [analyse exo-type =fn ?args]
  (|do [:let [[[=fn-type =fn-cursor] =fn-form] =fn]
        [=output-t =args] (analyse-apply* analyse exo-type =fn-type ?args)]
    (return (&/|list (&&/|meta =output-t =fn-cursor
                               (&&/$apply =fn =args)
                               )))))

(defn analyse-apply [analyse cursor exo-type macro-caller =fn ?args]
  (|case =fn
    [_ (&&/$def ?module ?name)]
    (|do [[real-name [exported? ?type ?meta ?value]] (&&module/find-def! ?module ?name)]
      (if (&type/type= &type/Macro ?type)
        (|do [macro-expansion (fn [state]
                                (|case (macro-caller ?value ?args state)
                                  (&/$Right state* output)
                                  (&/$Right (&/T [state* output]))

                                  (&/$Left error)
                                  ((&/fail-with-loc error) state)))
              module-name &/get-module-name
              ;; :let [[r-prefix r-name] real-name
              ;;       _ (when (= "module:" r-name)
              ;;           (->> macro-expansion
              ;;                (&/|map (fn [ast] (str (&/show-ast ast) "\n")))
              ;;                (&/fold str "")
              ;;                (&/|log! (str 'macro-expansion " " (&/ident->text real-name) " @ " module-name "\n"))))]
              ]
          (&/flat-map% (partial analyse exo-type) macro-expansion))
        (do-analyse-apply analyse exo-type =fn ?args)))
    
    _
    (do-analyse-apply analyse exo-type =fn ?args))
  )

(defn analyse-case [analyse exo-type ?value ?branches]
  (|do [_ (&/assert! (> (&/|length ?branches) 0) "[Analyser Error] Cannot have empty branches in \"case\" expression.")
        =value (&&/analyse-1+ analyse ?value)
        :let [var?? (|case =value
                      [_ (&&/$var =var-kind)]
                      (&/$Some =value)

                      _
                      &/$None)]
        =match (&&case/analyse-branches analyse exo-type var?? (&&/expr-type* =value) ?branches)
        _cursor &/cursor]
    (return (&/|list (&&/|meta exo-type _cursor
                               (&&/$case =value =match)
                               )))))

(defn ^:private unravel-inf-appt [type]
  (|case type
    (&/$Apply (&/$Var _inf-var) =input+)
    (&/$Cons _inf-var (unravel-inf-appt =input+))

    _
    (&/|list)))

(defn ^:private clean-func-inference [$input $output =input =func]
  (|case =input
    (&/$Var iid)
    (|do [:let [=input* (next-parameter-type =func)]
          _ (&type/set-var iid =input*)
          =func* (&type/clean $input =func)
          =func** (&type/clean $output =func*)]
      (return (&/$UnivQ &/$Nil =func**)))
    
    (&/$Apply (&/$Var _inf-var) =input+)
    (&/fold% (fn [_func _inf-var]
               (|do [:let [$inf-var (&/$Var _inf-var)]
                     =inf-var (&type/resolve-type $inf-var)
                     _func* (clean-func-inference $inf-var $output =inf-var _func)]
                 (return _func*)))
             =func
             (unravel-inf-appt =input))

    (&/$Product _ _)
    (&/fold% (fn [_func _inf-var]
               (|do [:let [$inf-var (&/$Var _inf-var)]
                     =inf-var (&type/resolve-type $inf-var)
                     _func* (clean-func-inference $inf-var $output =inf-var _func)]
                 (return _func*)))
             =func
             (&/|reverse (&type/flatten-prod =input)))
    
    _
    (|do [=func* (&type/clean $input =func)
          =func** (&type/clean $output =func*)]
      (return =func**))))

(defn analyse-function* [analyse exo-type ?self ?arg ?body]
  (|case exo-type
    (&/$Var id)
    (|do [? (&type/bound? id)]
      (if ?
        (|do [exo-type* (&type/deref id)]
          (analyse-function* analyse exo-type* ?self ?arg ?body))
        ;; Inference
        (&type/with-var
          (fn [$input]
            (&type/with-var
              (fn [$output]
                (|do [[[function-type function-cursor] function-analysis] (analyse-function* analyse (&/$Function $input $output) ?self ?arg ?body)
                      =input (&type/resolve-type $input)
                      =output (&type/resolve-type $output)
                      inferred-type (clean-func-inference $input $output =input (embed-inferred-input =input =output))
                      _ (&type/check exo-type inferred-type)]
                  (return (&&/|meta inferred-type function-cursor
                                    function-analysis)))
                ))))))

    _
    (&/with-attempt
      (|do [exo-type* (&type/actual-type exo-type)]
        (|case exo-type*
          (&/$UnivQ _)
          (|do [$var &type/existential
                :let [(&/$Ex $var-id) $var]
                exo-type** (&type/apply-type exo-type* $var)]
            (&/with-scope-type-var $var-id
              (analyse-function* analyse exo-type** ?self ?arg ?body)))

          (&/$ExQ _)
          (&type/with-var
            (fn [$var]
              (|do [exo-type** (&type/apply-type exo-type* $var)
                    =expr (analyse-function* analyse exo-type** ?self ?arg ?body)]
                (&&/clean-analysis $var =expr))))
          
          (&/$Function ?arg-t ?return-t)
          (|do [[=scope =captured =body] (&&function/with-function ?self exo-type*
                                           ?arg ?arg-t
                                           (&&/analyse-1 analyse ?return-t ?body))
                _cursor &/cursor
                register-offset &&env/next-local-idx]
            (return (&&/|meta exo-type* _cursor
                              (&&/$function register-offset =scope =captured =body))))

          _
          (&/fail "")))
      (fn [err]
        (&/fail-with-loc (str err "\n" "[Analyser Error] Functions require function types: " (&type/show-type exo-type)))))
    ))

(defn analyse-function** [analyse exo-type ?self ?arg ?body]
  (|case exo-type
    (&/$UnivQ _)
    (|do [$var &type/existential
          :let [(&/$Ex $var-id) $var]
          exo-type* (&type/apply-type exo-type $var)
          [_ _expr] (&/with-scope-type-var $var-id
                      (analyse-function** analyse exo-type* ?self ?arg ?body))
          _cursor &/cursor]
      (return (&&/|meta exo-type _cursor _expr)))
    
    (&/$Var id)
    (|do [? (&type/bound? id)]
      (if ?
        (|do [exo-type* (&type/actual-type exo-type)]
          (analyse-function* analyse exo-type* ?self ?arg ?body))
        ;; Inference
        (analyse-function* analyse exo-type ?self ?arg ?body)))
    
    _
    (|do [exo-type* (&type/actual-type exo-type)]
      (analyse-function* analyse exo-type* ?self ?arg ?body))
    ))

(defn analyse-function [analyse exo-type ?self ?arg ?body]
  (|do [output (analyse-function** analyse exo-type ?self ?arg ?body)]
    (return (&/|list output))))

(defn analyse-def* [analyse optimize eval! compile-def ?name ?value ?meta exported? & [?expected-type]]
  (|do [_ &/ensure-directive
        module-name &/get-module-name
        ? (&&module/defined? module-name ?name)
        _ (&/assert! (not ?)
                     (str "[Analyser Error] Cannot re-define " (str module-name &/+name-separator+ ?name)))
        =value (&/without-repl-closure
                (&/with-scope ?name
                  (if ?expected-type
                    (&/with-expected-type ?expected-type
                      (&&/analyse-1 analyse ?expected-type ?value))
                    (&&/analyse-1+ analyse ?value))))
        =meta (&&/analyse-1 analyse &type/Code ?meta)
        ==meta (eval! (optimize =meta))
        def-value (compile-def ?name (optimize =value) ==meta exported?)
        _ &type/reset-mappings]
    (return (&/T [module-name (&&/expr-type* =value) def-value]))))

(defn analyse-def [analyse optimize eval! compile-def ?name ?value ?meta exported?]
  (|do [_ (analyse-def* analyse optimize eval! compile-def ?name ?value ?meta exported?)]
    (return &/$Nil)))

(defn analyse-def-type-tagged [analyse optimize eval! compile-def ?name ?value ?meta tags* exported?]
  (|do [[module-name def-type def-value] (analyse-def* analyse optimize eval! compile-def ?name ?value ?meta exported? &type/Type)
        _ (&/assert! (&type/type= &type/Type def-type)
                     "[Analyser Error] Cannot define tags for non-type.")
        tags (&/map% (fn [tag*]
                       (|case tag*
                         [_ (&/$Text tag)]
                         (return tag)

                         _
                         (&/fail-with-loc "[Analyser Error] Incorrect format for tags.")))
                     tags*)
        _ (&&module/declare-tags module-name tags exported? def-value)]
    (return &/$Nil)))

(defn analyse-def-alias [?alias ?original]
  (|let [[r-module r-name] ?original]
    (|do [_ (&&module/find-def r-module r-name)
          module-name &/get-module-name
          _ (&/without-repl-closure
             (&&module/define-alias module-name ?alias ?original))]
      (return &/$Nil))))

(defn ^:private merge-module-states
  "(-> Host Host Host)"
  [new old]
  (|let [merged-module-states (&/fold (fn [total new-module]
                                        (|let [[_name _module] new-module]
                                          (|case (&/get$ &&module/$module-state _module)
                                            (&&module/$Cached)
                                            (&/|put _name _module total)
                                            
                                            (&&module/$Compiled)
                                            (&/|put _name _module total)

                                            _
                                            total)))
                                      (&/get$ &/$modules old)
                                      (&/get$ &/$modules new))]
    (&/set$ &/$modules merged-module-states old)))

(defn ^:private merge-modules
  "(-> Text Module Module Module)"
  [current-module new old]
  (&/fold (fn [total* entry]
            (|let [[_name _module] entry]
              (if (or (= current-module _name)
                      (->> _module
                           (&/get$ &&module/$defs)
                           &/|length
                           (= 0)))
                ;; Do not modify the entry of the current module, to
                ;; avoid overwritting it's data in improper ways.
                ;; Since it's assumed the "original" old module
                ;; contains all the proper own-module information.
                total*
                (&/|put _name _module total*))))
          old new))

(defn ^:private merge-compilers
  "(-> Text Lux Lux Lux)"
  [current-module new old]
  (->> old
       (&/set$ &/$modules (merge-modules current-module
                                         (&/get$ &/$modules new)
                                         (&/get$ &/$modules old)))
       (&/set$ &/$seed (max (&/get$ &/$seed new)
                            (&/get$ &/$seed old)))
       (merge-module-states new)))

(def ^:private get-compiler
  (fn [compiler]
    (return* compiler compiler)))

(defn ^:private set-compiler [compiler*]
  (fn [_]
    (return* compiler* compiler*)))

(defn try-async-compilation [path compile-module]
  (|do [already-compiled? (&&module/exists? path)]
    (if (not already-compiled?)
      (compile-module path)
      (|do [_compiler get-compiler]
        (return (doto (promise)
                  (deliver (&/$Right _compiler))))))))

(defn analyse-module [analyse optimize eval! compile-module ?meta ?imports]
  (|do [_ &/ensure-directive
        =anns (&&/analyse-1 analyse &type/Code ?meta)
        ==anns (eval! (optimize =anns))
        module-name &/get-module-name
        _ (&&module/set-anns ==anns module-name)
        _imports (&&module/fetch-imports ?imports)
        current-module &/get-module-name
        =asyncs (&/map% (fn [_import]
                          (|let [[path alias] _import]
                            (&/without-repl
                             (&/save-module
                              (|do [_ (&/assert! (not (= current-module path))
                                                 (&/fail-with-loc (str "[Analyser Error] Module cannot import itself: " path)))
                                    active? (&&module/active-module? path)
                                    ;; TODO: Enrich this error-message
                                    ;; to explicitly show the cyclic dependency.
                                    _ (&/assert! (not active?)
                                                 (str "[Analyser Error] Cannot import a module that is mid-compilation { cyclic dependency }: " path " @ " current-module))
                                    _ (&&module/add-import path)
                                    _ (if (= "" alias)
                                        (return nil)
                                        (&&module/alias current-module alias path))]
                                (try-async-compilation path compile-module))))))
                        _imports)
        _compiler get-compiler
        _ (&/fold% (fn [compiler _async]
                     (|case @_async
                       (&/$Right _new-compiler)
                       (set-compiler (merge-compilers current-module _new-compiler compiler))

                       (&/$Left ?error)
                       (&/fail ?error)))
                   _compiler
                   =asyncs)]
    (return &/$Nil)))

(defn ^:private coerce
  "(-> Type Analysis Analysis)"
  [new-type analysis]
  (|let [[[_type _cursor] _analysis] analysis]
    (&&/|meta new-type _cursor
              _analysis)))

(defn analyse-ann [analyse eval! exo-type ?type ?value]
  (|do [=type (&&/analyse-1 analyse &type/Type ?type)
        ==type (eval! =type)
        _ (&type/check exo-type ==type)
        =value (&&/analyse-1 analyse ==type ?value)
        _cursor &/cursor]
    (return (&/|list (&&/|meta ==type _cursor
                               (&&/$ann =value =type)
                               )))))

(defn analyse-coerce [analyse eval! exo-type ?type ?value]
  (|do [=type (&&/analyse-1 analyse &type/Type ?type)
        ==type (eval! =type)
        _ (&type/check exo-type ==type)
        =value (&&/analyse-1+ analyse ?value)]
    (return (&/|list (coerce ==type =value)))))

(let [program-type (&/$Function (&/$Apply &type/Text &type/List)
                                (&/$Apply &type/Any &type/IO))]
  (defn analyse-program [analyse optimize compile-program ?program]
    (|do [_ &/ensure-directive
          =program (&&/analyse-1 analyse program-type ?program)
          _ (compile-program (optimize =program))]
      (return &/$Nil))))
