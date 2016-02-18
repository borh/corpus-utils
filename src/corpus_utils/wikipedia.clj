(ns corpus-utils.wikipedia
  (:require [corpus-utils.text :as text]
            [corpus-utils.utils :as utils]
            [corpus-utils.document :refer :all]
            [schema.core :as s]
            [clojure.java.io :as io]
            [corpus-utils.utils :as utils]
            [clojure.string :as str]))

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

(defn char-writing-system
  "Return writing system type of char.
  To get codepoint of char: `(.codePointAt char 0)`.
  Converting code point into char: `(char 0x3041)`."
  [^String ch]
  (let [code-point (.codePointAt ch 0)]
    (cond
     (and (>= code-point 0x3041) (<= code-point 0x309f)) :hiragana
     (and (>= code-point 0x4e00) (<= code-point 0x9fff)) :kanji
     (and (>= code-point 0x30a0) (<= code-point 0x30ff)) :katakana
     (or (and (>= code-point 65)    (<= code-point 122))    ; half-width alphabet (A-Za-z)
         (and (>= code-point 65313) (<= code-point 65370))  ; full-width alphabet (Ａ-Ｚａ-ｚ)
         (and (>= code-point 48)    (<= code-point 57))     ; half-width numbers  (0-9)
         (and (>= code-point 65296) (<= code-point 65305))) ; full-width numbers  (０-９)
     :romaji
     (or (= code-point 12289) (= code-point 65291) (= code-point 44)) :commas ; [、，,] <- CHECK
     :else :symbols)))

(s/defn is-japanese? :- s/Bool
  [doc :- [[s/Str] s/Str]]
  (let [[_ lines] doc
        text (str/join lines)
        length (count text)
        {:keys [hiragana katakana kanji]
         :or {hiragana 0 katakana 0 kanji 0}}
        (->> text (map str) (map char-writing-system) frequencies)]
    (if (and
         (> length 50)
         (> (/
             (+ hiragana katakana kanji)
             length)
            0.5))
      true)))

(s/defn process-doc :- DocumentSchema
  [metadata :- {:year s/Num :month s/Num :day s/Num}
   doc :- [[s/Str] s/Str]]
  (let [[[header] lines] doc
        paragraphs (-> lines
                       drop-last ; Drop closing </doc> and split into paragraphs and lines.
                       (text/lines->paragraph-sentences identity))]
    {:metadata (let [[id title] (extract-header header)]
                 (make-sources-record title (:year metadata) id))
     :paragraphs (update-in
                  (mapv (fn [sentences]
                          {:tags (if (and (= 1 (count sentences))
                                          (= \. (last (first sentences))))
                                   #{:title} ; Also subsumes subtitles.
                                   #{})
                           :sentences sentences})
                        paragraphs)
                  [0 :tags] conj :title)}))

(s/defn document-seq :- [DocumentSchema]
  "Given a suitable (quasi)XML file generated from a Wikipedia dump, returns a lazy sequence of maps containing sources meta-information and parsed paragraphs."
  [filename :- s/Str]
  (let [metadata (->> (utils/read-tsv (str filename ".metadata.tsv") true)
                      first
                      (map #(try (Integer/parseInt %) (catch Exception e)))
                      (zipmap [:year :month :day]))
        lines (line-seq (io/reader filename))]
    (sequence
     (comp
      (partition-by is-open?)
      (partition-all 2)
      (filter is-japanese?)
      (map (partial process-doc metadata)))
     lines)))

(comment
  (s/with-fn-validation (document-seq "/data/Wikipedia/jawiki-20160113-text-small.xml")))
