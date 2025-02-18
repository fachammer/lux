# Count lines of code

```
cd ~/lux/ && find . -name '*.lux' | xargs wc -l
```

---

# Clean all

```
cd ~/lux/luxc/ && lein clean && \
cd ~/lux/stdlib/ && lein clean && \
cd ~/lux/new-luxc/ && lein clean && \
cd ~/lux/lux-js/ && lein clean && \
cd ~/lux/lux-python/ && lein clean && \
cd ~/lux/lux-lua/ && lein clean && \
cd ~/lux/lux-ruby/ && lein clean && \
cd ~/lux/lux-php/ && lein clean && \
cd ~/lux/lux-cl/ && lein clean && \
cd ~/lux/lux-scheme/ && lein clean
```

---

# Old compiler

## Build & install

```
cd ~/lux/luxc/ && lein clean && lein install
```

## Run JBE

```
cd ~/lux/luxc/jbe/ && ./jbe.sh
```

---

# Leiningen plugin

## Install

```
cd ~/lux/lux-lein/ && lein install
```

---

# Standard Library

## Test

```
cd ~/lux/stdlib/ && lein clean && lein_2_7_1 with-profile bibliotheca lux auto test
cd ~/lux/stdlib/ && lein_2_7_1 with-profile bibliotheca lux auto test
```

## Install

```
cd ~/lux/stdlib/ && lein_2_7_1 install
```

## Generate documentation

```
cd ~/lux/stdlib/ && lein_2_7_1 with-profile scriptum lux auto build
```

---

# Licentia: License maker

## Build

```
cd ~/lux/stdlib/ && lein_2_7_1 with-profile licentia lux auto build
```

## Test

```
cd ~/lux/stdlib/ && lein_2_7_1 with-profile licentia lux auto test
```

## Run

```
cd ~/lux/stdlib/ && java -jar target/program.jar --input ../license.json --output ../license.txt
```

---

# JavaScript compiler

## Test

```
cd ~/lux/lux-js/ && lein_2_7_1 lux auto test
cd ~/lux/lux-js/ && lein clean && lein_2_7_1 lux auto test
```

## Build

```
cd ~/lux/lux-js/ && lein_2_7_1 lux auto build
cd ~/lux/lux-js/ && lein clean && lein_2_7_1 lux auto build
```

## Try

```
cd ~/lux/lux-js/ && time java -jar target/program.jar build --source ~/lux/stdlib/source --target ~/lux/stdlib/target --module test/lux
```

---

# Python compiler

## Test

```
cd ~/lux/lux-python/ && lein_2_7_1 lux auto test
cd ~/lux/lux-python/ && lein clean && lein_2_7_1 lux auto test
```

## Build

```
cd ~/lux/lux-python/ && lein_2_7_1 lux auto build
cd ~/lux/lux-python/ && lein clean && lein_2_7_1 lux auto build
```

## Try

```
cd ~/lux/lux-python/ && java -jar target/program.jar build --source ~/lux/stdlib/source --target ~/lux/stdlib/target --module test/lux
```

---

# Lua compiler

## Test

```
cd ~/lux/lux-lua/ && lein_2_7_1 lux auto test
cd ~/lux/lux-lua/ && lein clean && lein_2_7_1 lux auto test
```

## Build

```
cd ~/lux/lux-lua/ && lein_2_7_1 lux auto build
cd ~/lux/lux-lua/ && lein clean && lein_2_7_1 lux auto build
```

## Try

```
cd ~/lux/lux-lua/ && java -jar target/program.jar build --source ~/lux/stdlib/source --target ~/lux/stdlib/target --module test/lux
```

---

# Ruby compiler

## Test

```
cd ~/lux/lux-ruby/ && lein_2_7_1 lux auto test
cd ~/lux/lux-ruby/ && lein clean && lein_2_7_1 lux auto test
```

## Build

```
cd ~/lux/lux-ruby/ && lein_2_7_1 lux auto build
cd ~/lux/lux-ruby/ && lein clean && lein_2_7_1 lux auto build
```

## Try

```
cd ~/lux/lux-ruby/ && java -jar target/program.jar build --source ~/lux/stdlib/source --target ~/lux/stdlib/target --module test/lux
```

---

# PHP compiler

## Test

```
cd ~/lux/lux-php/ && lein_2_7_1 lux auto test
cd ~/lux/lux-php/ && lein clean && lein_2_7_1 lux auto test
```

## Build

```
cd ~/lux/lux-php/ && lein_2_7_1 lux auto build
cd ~/lux/lux-php/ && lein clean && lein_2_7_1 lux auto build
```

## Try

```
cd ~/lux/lux-php/ && java -jar target/program.jar build --source ~/lux/stdlib/source --target ~/lux/stdlib/target --module test/lux
```

---

# Common Lisp compiler

## Test

```
cd ~/lux/lux-cl/ && lein_2_7_1 lux auto test
cd ~/lux/lux-cl/ && lein clean && lein_2_7_1 lux auto test
```

## Build

```
cd ~/lux/lux-cl/ && lein_2_7_1 lux auto build
cd ~/lux/lux-cl/ && lein clean && lein_2_7_1 lux auto build
```

## Try

```
cd ~/lux/lux-cl/ && java -jar target/program.jar build --source ~/lux/stdlib/source --target ~/lux/stdlib/target --module test/lux
```

---

# Scheme compiler

## Test

```
cd ~/lux/lux-scheme/ && lein_2_7_1 lux auto test
cd ~/lux/lux-scheme/ && lein clean && lein_2_7_1 lux auto test
```

## Build

```
cd ~/lux/lux-scheme/ && lein_2_7_1 lux auto build
cd ~/lux/lux-scheme/ && lein clean && lein_2_7_1 lux auto build
```

## Try

```
cd ~/lux/lux-scheme/ && java -jar target/program.jar build --source ~/lux/stdlib/source --target ~/lux/stdlib/target --module test/lux
```

---

# New compiler

## Test

```
cd ~/lux/new-luxc/ && lein_2_7_1 lux auto test
cd ~/lux/new-luxc/ && lein clean && lein_2_7_1 lux auto test
```

## Build

```
cd ~/lux/new-luxc/ && lein_2_7_1 lux auto build
cd ~/lux/new-luxc/ && lein clean && lein_2_7_1 lux auto build
```

# REPL

```
cd ~/lux/new-luxc/ && java -jar target/program.jar repl --source ~/lux/stdlib/source --target ~/lux/stdlib/target
```

# Try

```
cd ~/lux/new-luxc/ && time java -jar target/program.jar build --source ~/lux/stdlib/source --target ~/lux/stdlib/target --module test/lux
```

