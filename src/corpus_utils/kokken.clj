(ns corpus-utils.kokken
  (:require [fast-zip.core :as fz]
            [clojure.xml :as xml]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [clojure.spec.alpha :as s]
            [corpus-utils.utils :as utils]
            [corpus-utils.document])
  (:import [fast_zip.core ZipperLocation]
           (java.io File)))

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

(defn filter-sentences
  [^ZipperLocation sentences-loc]
  (->> sentences-loc
       (map fz/node)
       (filter string?)
       ;; Remove extra spaces from XML. (FIXME)
       (map (comp (fn [s] (str/replace s " " "")) first))
       str/join))

(s/fdef filter-sentences
  :args (s/cat :sentences-loc #(instance? ZipperLocation %))
  :ret string?)

;; TODO: Turn <引用 種別="記事説明" 話者="記者"> into tags!
;; Refactor to BCCWJ format. (+ look for more elegant ways to get at this hierarchical data)
(defn partition-by-paragraph
  [^ZipperLocation m]
  (->> m                                                    ;; Reset zipper root node to current loc.
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
       (into [])))

(s/fdef partition-by-paragraph
  :args (s/cat :m #(instance? ZipperLocation %))
  :ret (s/coll-of (s/coll-of string?)))

;; FIXME Refactor this into its own thing, perhaps under a delay?
(defonce ndc-map (into {} (utils/read-tsv-URL (io/resource "ndc-3digits.tsv") false)))

(defn parse-document
  [corpus year number filename article-loc]
  (let [attrs (:attrs (fz/node article-loc))
        title (or (:題名 attrs) (:title attrs) "")
        author (or (:著者 attrs) (:author attrs) "")
        title-alt (:欄名 attrs)
        style (or (:文体 attrs) (:style attrs))
        script (or (:script attrs) "漢字かな")                  ;; TODO This can be a corpus-level attribute too. Meiroku is at this level, though. Is this a good default? Should keywordize this!
        genre (or (:ジャンル attrs) "")]
    {:paragraphs
     (->> article-loc
          fz/down
          (iterate fz/right)
          (take-while (complement nil?))
          ;;(take 2)
          (mapcat partition-by-paragraph)
          (map (fn [text] {:tags      #{}
                           :sentences text}))
          (into []))
     :metadata
     {:title     title
      :author    author
      :publisher corpus
      :year      (try (Integer/parseInt year)
                      (catch Exception e
                        (println "Caught exception: " e)))
      :basename  (fs/base-name filename ".xml")
      :corpus    corpus
      :script    script
      :category  [(get ndc-map (str/replace genre #"^NDC" "") "分類なし")]}}))

(s/fdef parse-document
  :args (s/cat :corpus string? :year string? :number string? :filename #(instance? File %) :article-loc #(instance? ZipperLocation %))
  :ret :corpus/document)

(defn parse-document-seq
  "Each XML file from The Sun corpus contains several articles, so we return a vector of documents."
  [filename]
  (let [root-loc (-> filename
                     io/input-stream
                     xml/parse
                     fz/xml-zip)
        attrs (-> root-loc fz/node :attrs)
        [corpus year number] [(get attrs :雑誌名 (:title attrs))
                              (get attrs :年 (:year attrs))
                              (get attrs :号 (:issue attrs))]]
    (->> root-loc
         fz/down                                            ;; Descend to the :記事 level.
         fz/rights
         ;; Documents can be empty...
         (remove nil?)
         (map fz/xml-zip)
         (map (partial parse-document corpus year number filename)))))

(s/fdef parse-document-seq
  :args (s/cat :filename #(instance? File %))
  :ret :corpus/documents)

(defn document-seq
  [options]
  (->> (fs/glob (str (:corpus-dir options) "/*.xml"))
       (mapcat parse-document-seq)))

(s/fdef document-seq
  :args (s/cat :options (s/keys :req-un [:corpus/dir string? :metadata/dir string?]))
  :ret :corpus/documents)
