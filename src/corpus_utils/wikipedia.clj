(ns corpus-utils.wikipedia
  (:require [corpus-utils.text :as text]
            [corpus-utils.utils :as utils]
            [corpus-utils.document :refer :all]
            [clojure.spec.alpha :as s]
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

(defn make-sources-record
  [title year id]
  {:title     title
   :author    "Wikipedia"
   :publisher "Wikipedia"
   :year      year
   :basename  (str "jw" id)
   :corpus    "Wikipedia"
   :category  ["Wikipedia" "百科事典"]})

(s/fdef make-sources-record
  :args (s/cat :title string? :year int? :id string?)
  :ret :document/metadata)

(defn char-writing-system
  "Return writing system type of char.
  To get codepoint of char: `(.codePointAt char 0)`.
  Converting code point into char: `(char 0x3041)`."
  [^String ch]
  (let [code-point (.codePointAt ch 0)]
    (cond
      (and (>= code-point 0x3041) (<= code-point 0x309f)) :hiragana
      (or (and (>= code-point 0x4e00) (<= code-point 0x9fff))
          (= code-point 0x3005)) :kanji                     ;; 0x3005 -> kanji repeat mark
      (and (>= code-point 0x30a0) (<= code-point 0x30ff)) :katakana
      (or (and (>= code-point 65) (<= code-point 122))      ; half-width alphabet (A-Za-z)
          (and (>= code-point 65313) (<= code-point 65370)) ; full-width alphabet (Ａ-Ｚａ-ｚ)
          (and (>= code-point 48) (<= code-point 57))       ; half-width numbers  (0-9)
          (and (>= code-point 65296) (<= code-point 65305))) ; full-width numbers  (０-９)
      :romaji
      (or (= code-point 12289) (= code-point 65291) (= code-point 44)) :commas ; [、，,] <- CHECK
      :else :symbols)))

(defn is-japanese?
  [doc]
  (let [[_ lines] doc
        text (str/join lines)
        length (count text)
        {:keys [hiragana katakana kanji]
         :or   {hiragana 0 katakana 0 kanji 0}}
        (->> text (map str) (map char-writing-system) frequencies)]
    (if (and
          (> length 50)
          (> (/
               (+ hiragana katakana kanji)
               length)
             0.5))
      true)))

(s/fdef is-japanese?
  :args (s/cat :doc (s/coll-of any?))
  :ret boolean?)

(defn process-doc
  [metadata doc]
  (let [[[header] lines] doc
        paragraphs (text/lines->paragraph-sentences
                     (->> lines
                          drop-last                         ; Drop closing </doc> and split into paragraphs and lines.
                          (map text/normalize-nfkc)
                          (map text/convert-half-to-fullwidth))
                     identity)]
    {:metadata   (let [[id title] (extract-header header)]
                   (make-sources-record title (:year metadata) id))
     :paragraphs (update-in
                   (mapv (fn [sentences]
                           {:tags      (if (and (= 1 (count sentences))
                                                (= \. (last (first sentences))))
                                         #{:title}          ; Also subsumes subtitles.
                                         #{})
                            :sentences sentences})
                         paragraphs)
                   [0 :tags] conj :title)}))                ; NOTE: the first sentence is always a title.

(s/def :date/year int?)
(s/def :date/month (into #{} (range 1 13)))
(s/def :date/day (into #{} (range 1 32)))
(s/fdef process-doc
  :args (s/cat :metadata (s/keys :req-un [:date/year :date/month :date/day])
               :doc (s/coll-of (s/cat :header (s/coll-of string?) :text string?)))
  :ret :corpus/document)

(defn document-seq
  "Given a suitable (quasi)XML file generated from a Wikipedia dump, returns a lazy sequence of maps containing sources meta-information and parsed paragraphs."
  [options]
  (let [metadata (->> (utils/read-tsv (str (:corpus-file options) ".metadata.tsv") true)
                      first
                      (map #(try (Integer/parseInt %) (catch Exception e)))
                      (zipmap [:year :month :day]))
        lines (line-seq (io/reader (:corpus-file options)))]
    (sequence
      (comp
        (partition-by is-open?)
        (partition-all 2)
        (filter is-japanese?)
        (map (partial process-doc metadata)))
      lines)))

(s/fdef document-seq
  :args (s/cat :options (s/keys :req-un [:corpus/dir string? :metadata/dir string?]))
  :ret :corpus/documents)

(comment
  (s/with-fn-validation (document-seq "/data/Wikipedia/jawiki-20160113-text-small.xml")))
