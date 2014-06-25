(ns corpus-utils.kokken
  (:require [fast-zip.core :as fz]
            [clojure.xml :as xml]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [schema.core :as s]
            [schema.macros :as sm]
            [corpus-utils.text :as text]
            [corpus-utils.document :refer [DocumentSchema]])
  (:import [fast_zip.core ZipperLocation]))

;; :sentence -> :s
;; + metadata is in file

;; ## XML Extraction
;;
;; XML is extracted to a tagged paragraph/sentence data structure of the form:
;;
;;     [{:tags #{:some-tag, :another-tag},
;;       :sentences ["First.", "Second sentence."]},
;;      {:tags #{ ... },
;;       :sentences [ ... ]}]

(sm/defn filter-sentences :- s/Str
  [sentences-loc :- [ZipperLocation]]
  (->> sentences-loc
       (map fz/node)
       (filter string?)
       ;; Remove extra spaces from XML. (FIXME)
       (map (comp (fn [s] (str/replace s " " "")) first))
       str/join))

;; TODO: Turn <引用 種別="記事説明" 話者="記者"> into tags!
;; Refactor to BCCWJ format. (+ look for more elegant ways to get at this hierarchical data)
(sm/defn partition-by-paragraph :- [[s/Str]]
  [m :- ZipperLocation]
  (->> m ;; Reset zipper root node to current loc.
       fz/node
       fz/xml-zip
       (iterate fz/next)
       (take-while (complement fz/end?))
       (partition-by #(= (:tag (fz/node %)) :br))
       (map #(->> %
                  (partition-by (fn [m] (= :s (:tag (fz/node m)))))
                  (map filter-sentences)
                  (remove empty?)
                  (into [])))
       (remove empty?)
       (into [])
       #_((fn [a] (doto a println)))))

(defonce ndc-map (into {} (text/read-tsv-URL (io/resource "ndc-3digits.tsv") false)))

(sm/defn parse-document :- DocumentSchema
  [corpus :- s/Str
   year :- s/Str
   number :- s/Str
   filename :- java.io.File
   article-loc :- ZipperLocation]
  (let [[title author title-alt style genre] ((juxt :題名 :著者 :欄名 :文体 :ジャンル) (:attrs (fz/node article-loc)))]
    {:paragraphs
     (->> article-loc
          fz/down
          (iterate fz/right)
          (take-while (complement nil?))
          ;;(take 2)
          (mapcat partition-by-paragraph)
          (map (fn [text] {:tags #{}
                          :sentences text}))
          (into []))
     :metadata
     {:title     title
      :author    author
      :publisher corpus
      :year      (Integer/parseInt year)
      :basename  (fs/base-name filename ".xml")
      :corpus    corpus
      :category  [(get ndc-map (str/replace genre #"^NDC" "") "分類なし")]}}))

(sm/defn parse-document-seq :- [DocumentSchema]
  "Each XML file from The Sun corpus contains several articles, so we return a vector of documents."
  [filename :- java.io.File]
  (let [root-loc (-> filename
                     io/input-stream
                     xml/parse
                     fz/xml-zip)
        [corpus year number] ((juxt :雑誌名 :年 :号) (-> root-loc fz/node :attrs ))]
    (->> root-loc
         fz/down ;; Descend to the :記事 level.
         fz/rights
         ;; Documents can be empty...
         (remove nil?)
         (map fz/xml-zip)
         (map (partial parse-document corpus year number filename)))))

(sm/defn document-seq
  [data-dir :- s/Str]
  (->> (fs/glob (str data-dir "/*.xml"))
       (mapcat parse-document-seq)))

(comment
  (sm/with-fn-validation (parse "/data/taiyo-corpus/XML/t189506.xml")))
