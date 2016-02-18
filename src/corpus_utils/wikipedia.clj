(ns corpus-utils.wikipedia
  (:require [corpus-utils.text :as text]
            [corpus-utils.utils :as utils]
            [corpus-utils.document :refer :all]
            [schema.core :as s]
            [clojure.java.io :as io]
            [corpus-utils.utils :as utils]))

(defn is-open? [s]
  (re-seq #"^<doc id" s))

(defn is-close? [s]
  (re-seq #"^</doc>$" s))

(defn extract-header [s]
  (->> s
       (re-seq #"^<doc id=\"(\d+)\" url=\".+\" title=\"(.+)\">$")
       first
       rest
       vec))

(s/defn make-sources-record :- MetadataSchema
  [title :- s/Str
   year :- s/Num
   id :- s/Str]
  {:title     title
   :author    "Wikipedia"
   :publisher "Wikipedia"
   :year      year
   :basename  (str "jw" id)
   :corpus    "Wikipedia"
   :category  ["Wikipedia" "百科事典"]})

(s/defn process-doc :- DocumentSchema
  [metadata
   doc]
  (let [[[header] lines] doc]
    {:metadata (let [[id title] (extract-header header)]
                 (make-sources-record title (:year metadata) id))
     :paragraphs (-> lines drop-last text/lines->paragraph-sentences text/add-tags)})) ; Drop closing </doc>, split and add (dummy) tags.

(s/defn document-seq :- [DocumentSchema]
  "Given a suitable (quasi)XML file generated from a Wikipedia dump, returns a lazy sequence of maps containing sources meta-information and parsed paragraphs."
  [filename :- s/Str]
  (let [metadata (->> (utils/read-tsv (str filename ".metadata.tsv") true) first (map #(try (Integer/parseInt %) (catch Exception e))) (zipmap [:year :month :day]))
        lines (line-seq (io/reader filename))]
    (->> lines
         (partition-by is-open?)
         (partition-all 2)
         (map (partial process-doc metadata)))))

(comment
  (s/with-fn-validation (document-seq "/data/Wikipedia/jawiki-20160113-text-small.xml")))
