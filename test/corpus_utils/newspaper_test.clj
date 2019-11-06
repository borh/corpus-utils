(ns corpus-utils.newspaper-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [orchestra.spec.test :as st]
            [expound.alpha :as expound]
            [corpus-utils.newspaper :refer :all]
            [me.raynes.fs :as fs]))

(st/instrument)
(stest/check (stest/enumerate-namespace 'corpus-utils.newspaper))
(alter-var-root #'s/*explain-out* (constantly (expound/custom-printer {:show-valid-values? true :print-specs? false :theme :figwheel-theme})))

(def options
  [{:corpus-dir (fs/file "/home/bor/Corpora/Newspapers/Yomiuri2004")}
   {:corpus-dir (fs/file "/home/bor/Corpora/Newspapers/Mainichi")}])

(deftest document-seq-test
  (testing "whole corpus parsing"
    (doseq [corpus-options options]
      (time (document-seq corpus-options))))
  (testing "document parsing"
    (doseq [corpus-options options]
      (doseq [doc (take 10 (time (document-seq corpus-options)))]
        (is (pos? (count (:document/paragraphs doc))))
        (is (s/valid? :corpus/document doc))))))
