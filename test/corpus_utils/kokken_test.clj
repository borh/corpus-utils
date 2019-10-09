(ns corpus-utils.kokken-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [orchestra.spec.test :as st]
            [expound.alpha :as expound]
            [corpus-utils.kokken :refer :all]))

(st/instrument)
(stest/check (stest/enumerate-namespace 'corpus-utils.kokken))
(alter-var-root #'s/*explain-out* (constantly (expound/custom-printer {:show-valid-values? true :print-specs? false :theme :figwheel-theme})))

(let [corpus-prefix "/home/bor/Corpora/"]
  (def options-vec
    [{:name "Taiyo" :corpus-dir (str corpus-prefix "taiyo-corpus/XML/")}
     {:name "Josei" :corpus-dir (str corpus-prefix "josei_xml/xml/")}
     {:name "Meiroku" :corpus-dir (str corpus-prefix "meiroku_xml/")}
     {:name "Kokumin no tomo" :corpus-dir (str corpus-prefix "kokumin_xml/")}]))

(deftest document-seq-test
  (doseq [options options-vec]
    (testing (:name options)
      (doseq [doc (take 10 (document-seq options))]
        (is (pos? (count (:document/paragraphs doc))))
        (is (s/valid? :corpus/document doc))))))
