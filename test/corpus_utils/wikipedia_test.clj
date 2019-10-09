(ns corpus-utils.wikipedia-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [orchestra.spec.test :as st]
            [expound.alpha :as expound]
            [corpus-utils.wikipedia :refer :all]))

(st/instrument)
(stest/check (stest/enumerate-namespace 'corpus-utils.wikipedia))
(alter-var-root #'s/*explain-out* (constantly (expound/custom-printer {:show-valid-values? true :print-specs? false :theme :figwheel-theme})))

(def options
  {:corpus-dir "/home/bor/Corpora/Wikipedia/Ja/jawiki-20191001-pages-articles-wikiextractor-categories.xml.xz"})

(deftest document-seq-test
  (testing "document parsing"
    (doseq [doc (take 10 (document-seq options))]
      (is (pos? (count (:document/paragraphs doc))))
      (is (s/valid? :corpus/document doc)))))
