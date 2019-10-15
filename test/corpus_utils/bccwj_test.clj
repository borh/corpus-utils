(ns corpus-utils.bccwj-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [orchestra.spec.test :as st]
            [expound.alpha :as expound]
            [corpus-utils.bccwj :refer :all]))

(st/instrument)
(stest/check (stest/enumerate-namespace 'corpus-utils.bccwj))
(alter-var-root #'s/*explain-out* (constantly (expound/custom-printer {:show-valid-values? true :print-specs? false :theme :figwheel-theme})))

(def options
  {:metadata-dir "/home/bor/Projects/bccwj/DOC/"
   :corpus-dir   "/home/bor/Projects/bccwj/C-XML/VARIABLE/"})

(deftest parse-metadata-test
  (let [m (vals (parse-metadata (:metadata-dir options)))]
    (testing "valid metadata spec"
      (doseq [d (take 10 m)]
        (is (s/valid? :document/metadata d))))
    (testing "correctly assigned c-codes"
      (is (= 66 (count (into #{} (comp (map :metadata/topic) (filter identity)) m))))
      (is (= 8 (count (into #{} (comp (map :metadata/audience) (filter identity)) m))))
      (is (= 9 (count (into #{} (comp (map :metadata/media) (filter identity)) m)))))))

(deftest document-seq-test
  (testing "document parsing"
    (doseq [doc (take 10 (document-seq options))]
      (is (pos? (count (:document/paragraphs doc))))
      (is (s/valid? :corpus/document doc)))))

(comment
  (def metadata (into #{} (map :document/metadata (document-seq options))))
  (def c-topics (into #{} (comp (map :metadata/topic) (filter identity)) metadata))
  (def ndc-topics (into #{} (comp (map :metadata/category) (filter identity)) metadata))
  (clojure.set/intersection c-topics ndc-topics)
  ;; How to merge these intersecting labels that are recorded at different levels?
  #{"情報科学" "宗教" "商業" "旅行" "物理学" "総記" "仏教" "キリスト教" "化学" "機械" "哲学" "ドイツ語" "家事" "法律" "数学" "日本語" "教育" "伝記" "社会" "生物学" "水産業"})