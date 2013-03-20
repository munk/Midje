(ns midje.parsing.0-to-fact-form.t-tabular
  (:use [midje.parsing.0-to-fact-form.tabular :except [add-binding-note table-binding-maps]]
        [midje.data.metaconstant :only [metaconstant-symbol?]]
        [midje sweet test-util]
        [ordered.map :only (ordered-map)]
        midje.util)
  (:require [midje.util.pile :as pile]
            [midje.parsing.lexical-maps :as maps]
            [midje.parsing.1-to-explicit-form.facts :as parse-facts]
            [midje.data.fact :as fact-data]
            [midje.data.compendium :as compendium]
            [midje.config :as config]))

(expose-testables midje.parsing.0-to-fact-form.tabular)


(tabular "no longer need to prefix table variables with '?'"
 (fact (+ a b) => result )

     a     b       result
     1     2       (fn [actual] (= 3 actual))
     3     4       (as-checker odd?)
     2     1       (as-checker (fn [actual] (= 3 actual))))

(let [c 1]
  (tabular "considers local bindings to be table values, not headings"
    (fact (+ a b) => result )
  
       a  b      result
       c  2      3       ;; need second row, else (in the failure case) this  
       4  5      9))     ;; mis-parsing tabular will think there are 0 rows     

(defn f? [x] true)

(tabular "won't consider bound resolvable fns as table heading var"
  (fact (+ a b) => result)    
     result  a   b
     f?      1   2    ;; needs second row, like above 
     17      8   9)
                      
(tabular "won't think of metaconstants as unbound symbols, and thus 
          won't try to make them table heading variables"
  (fact 'candidate arrow metaconstant-symbol?)
     candidate   arrow
     ---foo---     => 
     -foo-         => )

(tabular "The tabular form can have a doc string"
 (fact 
   (+ ?a ?b) => ?result)
 ?a    ?b      ?result
 1     2       3)

(tabular
 (fact "will ignore optional pipes separating table columns"
   (str a b c) => result)

 a   | b   | c   | result
 "a" | "|" | "c" | "a|c" )

(tabular
 (fact "will ignore an optional ':where' above the table"
   (str a b) => result)

 :where
 a      | b      | result
 'where | :where | "where:where") ;; just to makes sure

(tabular
 (fact "will ignore an optional 'where' above the table"
   (+ a b) => result)

 where
 a  |  b | result
 1  | 2  | 3)

(tabular
 (fact "can have different forms of where or pipe in the data, no problem"
   (str a b c d) => result)

 where
 a      | b   | c      | d  | result
 :where | "|" | 'where | '| | ":where|where|")





;; Table Validation

(silent-tabular
 (fact 
   (tabular-forms '?forms) => '?expected
   ?forms                       ?expect
   [ fact table ]               [fact table]))
(note-that (fact-failed-with-note #"There's no table"))

(silent-tabular (fact nil => nil))
(note-that (fact-failed-with-note #"There's no table"))

(silent-tabular "doc string present" (fact nil => nil))
(note-that (fact-failed-with-note #"There's no table"))

(silent-tabular
 (fact ?a => ?b)
   ?a   ?b)
(note-that (fact-failed-with-note #"It looks like the table has headings, but no values"))
 
(silent-tabular
 (fact
   (+ a b) => result)
 2    4   999     )
(note-that (fact-failed-with-note #"It looks like the table has no headings"))

;; Other tests via midje.sweet API

(unfinished g)
(defn f [n] (inc (g n)))

(tabular
 (fact
   (f ?n) => ?result
   (provided
     (g ?n) => ?intermediate))
 ?result ?n ?intermediate
 2       1      1
 (+ 1 2) 1      2
 3       2      2)

                                     
(tabular
 (fact "only two numbers have the same sum and square"
   (* ?n ?n) ?arrow (+ ?n ?n))
 ?n        ?arrow
 0         =>      
 2         =>
 ;; Failure cases
 1         =not=>
 (* 10 10) =not=>
 ;; and so on
 )

(defn alive? [cell-status neighbor-count]
  (cond (= cell-status :dead)
        (= 3 neighbor-count)

        :else
        (some #{neighbor-count} #{2 3})))

(tabular
  (fact "The rules of Conway's life"
    (alive? ?cell-status ?neighbor-count) => ?expected)

  ?cell-status   ?neighbor-count   ?expected
  :alive         1                 FALSEY        ; underpopulation
  :alive         2                 truthy       
  :alive         3                 truthy
  :alive         4                 FALSEY        ; overpopulation
  
  ;; A newborn cell has three parents
  :dead          2                 FALSEY
  :dead          3                 truthy
  :dead          4                 FALSEY)

(tabular
 (fact "nice fact properties are retained"
   (let [a 1]
     (f ?n) => ?result
     (provided
       (g ?n) => ?intermediate)))
 ?result ?n ?intermediate
 (+ a 1)       1      1)

(config/with-augmented-config {:visible-future true}
  (capturing-fact-output
   (tabular
     (future-fact (inc ?int) => ?int)
     ?int
     1)
   (fact @fact-output => #"WORK TO DO")))

;; Util: table-binding-maps
 
(fact "gets the bindings off fact table"
  (table-binding-maps ['?a  '?b '?result] [1 2 3])
  => [ (ordered-map '?a 1, '?b 2, '?result 3) ])


(defn filter-checkable-maps [form]
  (filter maps/checkable-map? (flatten form)))

(defn expand-and-add-binding-note
  ([form binding-map]
     (add-binding-note (parse-facts/midjcoexpand form) binding-map))
  ([form]
     (expand-and-add-binding-note form (ordered-map '?a 'a))))

(def binding-notes-from (comp filter-checkable-maps expand-and-add-binding-note))

(fact "binding notes"
  (fact "can be inserted"
    (binding-notes-from '(fact 1 => 1)) => (just (contains {:binding-note "[?a a]"})))
  
  (fact "are inserted into every checkable"
    (binding-notes-from '(fact 1 => 1
                               2 => 2)) => (just (contains {:binding-note "[?a a]"})
                                                (contains {:binding-note "[?a a]"})))
  
  (future-fact "are added to nested facts"
    (binding-notes-from '(fact 1 => 1 (fact 2 => 2))) => (just (contains {:binding-note "[?a a]"})
                                                               (contains {:binding-note "[?a a]"})))

  (fact "are not added to prerequisites"
    (binding-notes-from '(fact (f 1) => 1 (provided (g 1) => 2))) => (just (contains {:binding-note "[?a a]"})))

  (fact "are added in the left-to-right order of the original table"
    (let [result (binding-notes-from '(fact (+ 1 2) => 3) (ordered-map :c 1, :b 2, :a 3))]
      (count result) => 1
      (:binding-note (first result)) => #"(?s):c 1.*:b 2.*:a 3")))
    
    
;; tabular doc-string prints in report

(tabular "table of results"
  (silent-fact (+ a b) => result)
    
      a    b   result
      2    4   999     )  ;; PURPOSELY FAIL 
(note-that fact-fails, (fact-described-as  "table of results" nil))



(tabular
  (silent-fact "add stuff"  ;; Note that this gets promoted to be with `tabular`
      (+ a b) => result)
    
      a    b   result
      2    4   999     )  ;; PURPOSELY FAIL
(note-that fact-fails, (fact-described-as  "add stuff" nil))


;;; Bug fixes

(unfinished g)

(defn f [arg] (g arg))

(def before-fact (atom 0))
(def before-check (atom 0))

(against-background [(g "OK3") => 3
                     (before :facts (swap! before-fact inc))
                     (before :checks (swap! before-check inc))]
  (tabular
    (fact
      (f ?foo) => 3
      (f ?foo) => 3)
    ?foo
    "OK3"))

(fact
  @before-fact => 1
  @before-check => 2)

;;; Tabular facts have appropriate source and body source.

(tabular (fact ?a => 1) ?a 1 1)
(fact :check-only-at-load-time
  (fact-data/source (compendium/last-fact-checked<>)) => '(tabular (fact ?a => 1) ?a 1 1)
  (fact-data/guid (compendium/last-fact-checked<>)) => (pile/form-guid '((fact ?a => 1) ?a 1 1)))
  
(tabular "name" :integration (fact ?a => 1) ?a 1 1)
(fact :check-only-at-load-time
  (fact-data/source (compendium/last-fact-checked<>)) => '(tabular "name" :integration (fact ?a => 1) ?a 1 1)
  (fact-data/guid (compendium/last-fact-checked<>)) => (pile/form-guid '((fact ?a => 1) ?a 1 1))
  (:integration (meta (compendium/last-fact-checked<>))) => true)
  
(tabular (fact "name" :integration ?a => 1) ?a 1 1)
(fact :check-only-at-load-time
  (fact-data/source (compendium/last-fact-checked<>)) => '(tabular (fact "name" :integration ?a => 1) ?a 1 1)
  (fact-data/guid (compendium/last-fact-checked<>)) => (pile/form-guid '((fact ?a => 1) ?a 1 1))
  (:integration (meta (compendium/last-fact-checked<>))) => true)
  


