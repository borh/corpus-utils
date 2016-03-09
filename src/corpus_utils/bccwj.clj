(ns corpus-utils.bccwj
  (:require ;;[clojure.zip :as z]
            [fast-zip.core :as fz]
            [clojure.xml :as xml]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.core.reducers :as r]
            ;;[org.httpkit.client :as http]
            [plumbing.core :refer [for-map map-vals]]
            [schema.core :as s]
            [corpus-utils.c-code :refer [c-code]]
            [corpus-utils.utils :as utils]
            [corpus-utils.document :refer [MetadataSchema DocumentSchema SentencesSchema]])
  (:import [fast_zip.core ZipperLocation]
           [java.util.zip ZipFile ZipEntry]))

;; # Importer for BCCWJ-Formatted C-XML Data
;;
;; Imports C-XML-type data.
;; M-XML is also supported, but is not recommended as we do our own parsing.


;; ## Utility functions
;; TODO integration
(s/defn walk-zip-files
  [zip-name :- java.io.File]
  (let [z (ZipFile. zip-name)]
    (for [e (enumeration-seq (.entries z))
          :when (not (.isDirectory ^ZipEntry e))]
      (.getInputStream z e))))

;; ## XML Tags
;;
;; Definition of XML tags that trigger a paragraph break.
;; Sentence breaks are triggered by the :sentence tag.
;; Refer to BCCWJ 1.0 Manual V1 Table 5.2 (pp. 78) for all tags and their meanings.
(def paragraph-level-tags
  #{:article
    :blockEnd
    :cluster
    :titleBlock
    :title
    :orphanedTitle
    :list
    :paragraph
    :verse
    :br ; Inline tag. TODO: find example where this is right or wrong.
    :speech
    :speaker ; For OM.
    :caption
    :citation
    :quotation
    :OCAnswer
    :OCQuestion})

(comment ; Not used at present. The text attr should not be used as a string in the sentence.
  (def in-sentence-tags
    #{:ruby
      :correction
      :missingCharacter
      :enclosedCharacter
      :er
      :cursive
      :image ;; Inline tag (Emoji).
      :noteBodyInline ;; Inline tag.
      :noteMarker ;; Inline tag.
      :superScript
      :subScript
      :fraction
      :delete
      :br
      :verseLine
      :info
      :rejectedSpan
      :substitution
      :quote
      :citation
      :sentence}))

;; ## XML Extraction
;;
;; XML is extracted to a tagged paragraph/sentence data structure of the form:
;;
;;     [{:tags #{:some-tag, :another-tag},
;;       :sentences ["First.", "Second sentence."]},
;;      {:tags #{ ... },
;;       :sentences [ ... ]}]

(s/defn backtrack-with-distance :- {:loc ZipperLocation :depth s/Num}
  "Modified from `fast-zip.core/next` source. Like zip/next, but also keeps track of how far up the tree it goes."
  [loc :- ZipperLocation]
  (loop [p loc
         depth 0]
    (if (and (not (identical? :end (.path p))) (fz/up p))
      (or (if-let [r (fz/right (fz/up p))] {:loc r :depth (inc depth)}) (recur (fz/up p) (inc depth)))
      {:loc (ZipperLocation. (.ops loc) (.node loc) :end)
       :depth depth})))

;; FIXME break into emitter and consume-sequence-and-build-sentences functions; naming: next-direction conflates direction and depth

(s/defn walk-and-emit
  "Traverses xml-data (parsed with clojure.xml/parse or clojure.data.xml/parse) using a zipper and incrementally builds up and returns the document as a vector of maps (representing paragraphs), each element of which contains tags and a vector of sentences."
  [xml-data]
  (loop [xml-loc (fz/xml-zip xml-data)
         tag-stack []
         par-loc (fz/down (fz/vector-zip [{:tags [] :sentences []}]))]
    (if (fz/end? xml-loc)
      (fz/root par-loc)
      (let [xml-node (fz/node xml-loc)
            tag (:tag xml-node)
            xml-loc-down  (and (fz/branch? xml-loc) (fz/down xml-loc))
            xml-loc-right (and (not xml-loc-down) (fz/right xml-loc))
            xml-loc-up    (and (not xml-loc-down) (not xml-loc-right) (backtrack-with-distance xml-loc)) ; At lowest depth for this paragraph.
            next-direction (cond xml-loc-down :down
                                 xml-loc-right :same
                                 :else (:depth xml-loc-up))
            coded-tag (if (#{:speech :speaker :title :titleBlock :orphanedTitle :list :caption :citation :quotation :OCAnswer :OCQuestion} tag)
                        (tag {:titleBlock :title :orphanedTitle :title} tag))
            new-tag-stack (case next-direction
                            :down (conj tag-stack coded-tag)
                            :same (if (= :br tag) tag-stack (conj (pop tag-stack) coded-tag)) ; :br is an exception as a paragraph break, as it does not increase XML tree depth
                            ((apply comp (repeat next-direction pop)) tag-stack))] ; Discard equal to up depth.
        (recur
         (or xml-loc-down xml-loc-right (:loc xml-loc-up)) ; Same as (fz/next xml-loc).
         new-tag-stack
         (cond (paragraph-level-tags tag) ; Insert new paragraph, inserting the new tag stack.
               (-> par-loc (fz/insert-right {:tags new-tag-stack :sentences [""]}) fz/right)

               (= :sentence tag) ; Insert new sentence.
               (let [tag-stack (-> par-loc fz/node :tag-stack)]
                 (fz/edit par-loc update-in [:sentences] conj ""))

               (string? xml-node) ; Update last-inserted sentence's text.
               (fz/edit par-loc update-in [:sentences (-> par-loc fz/node :sentences count dec)] #(str % xml-node))

               :else par-loc)))))) ; Do nothing.

;; ## Metadata
;;
;; Metadata is not contained in the XML files and must be read from the Joined_info.zip (CSV) file distributed with the BCCWJ 1.0 DVD.


;; # ISBN metadata search
;;
;; This should only be run once to generate a static file, with (perhaps) regular updates, if it makes sense for some types of metadata that could perhaps change.
;; What we're after: http://iss.ndl.go.jp/books/R100000002-I000001786643-00.json
(comment
  (def isbns (take 1 (map #(nth % 8) (utils/read-tsv (str "/data/BCCWJ-2012-dvd1/DOC/" "Joined_info.txt") true))))
  (take 1 (utils/read-tsv (str "/data/BCCWJ-2012-dvd1/DOC/" "Joined_info.txt") true))
  isbns

  (defn query-ndl [isbn]
    (let [options {:form-params {:name "http-kit" :features ["async" "client" "server"]}}
          {:keys [status error]} @(http/post "http://iss.ndl.go.jp/api/opensearch?isbn=4795274053" options)]
      (if error
        (println "Failed, exception is " error)
        (println "Async HTTP POST: " status)))))

(s/defn parse-metadata :- [MetadataSchema]
  [metadata-dir :- s/Str]
  ;; metadata-dir "/data/BCCWJ-2012-dvd1/DOC/"
  (let [metadata  (utils/read-tsv (str metadata-dir "Joined_info.txt") true)
        copyright (into {} (utils/read-tsv (str metadata-dir "CopyRight_Annotation.txt") true))
        ndc-map   (into {} (utils/read-tsv-URL (io/resource "ndc-3digits.tsv") false))
        c-map     (for-map [[k1 v1] (c-code "1")
                            [k2 v2] (c-code "2")
                            [k3 v3] (c-code "34")]
                           (str k1 k2 k3) {:audience v1 :media v2 :topic v3})
        name-map  {"LB" "書籍"
                   "PB" "書籍"
                   "OB" "書籍"
                   "OL" "法律"
                   "OW" "白書"
                   "OM" "国会会議録"
                   "OP" "広報紙"
                   "PN" "新聞"
                   "PM" "雑誌"
                   "OV" "韻文"
                   "OT" "検定教科書"
                   "OY" "Yahoo!ブログ"
                   "OC" "Yahoo!知恵袋"}

        bccwj-metadata
        (r/reduce
         (r/monoid
          (fn [m
              [basename _ title subtitle _ _ publisher year isbn _
               genre-1 genre-2 genre-3 genre-4 _ _
               author author-year author-gender corpus-name]]
            (assoc m basename
             {:title     (str title (if-not (= "" (string/trim subtitle)) ": " subtitle))
              :author    author
              :gender    (let [authors (string/split author-gender #"/")]
                           (if (every? #(= (first authors) %) authors)
                             (case (first authors)
                               "男" :male
                               "女" :female
                               ""   nil
                               :mixed)))
              :author-year (try (Integer/parseInt author-year) (catch Exception e))
              :publisher (if (empty? publisher) "BCCWJ" publisher)
              :copyright (copyright basename)
              :audience  (if (= (name-map corpus-name) "書籍") (:audience (c-map genre-3)))
              :media     (if (= (name-map corpus-name) "書籍") (:media (c-map genre-3)))
              :topic     (if (= (name-map corpus-name) "書籍") (:topic (c-map genre-3)))
              :year      (Integer/parseInt year)
              :basename  basename
              :corpus    "BCCWJ"
              :subcorpus corpus-name
              ;;:subcorpus-ja (name-map corpus-name) ;; FIXME
              :category (->> (condp re-seq corpus-name
                               #"(L|O|P)B" (->> [genre-1 genre-2 genre-3 genre-4]
                                                (r/map #(ndc-map % %))
                                                (r/map #(string/replace % #"^\d\s" ""))
                                                (r/remove #(= "分類なし" %)) ;; Should be replaced with C-CODE. FIXME ["分類なし" "8328"]
                                                (r/remove #(re-seq #"^\d{4}$" %))
                                                #_(r/map #(c-map % %)))
                               #"OL" (r/map #(string/replace % #"\d\d\s" "")
                                            [genre-1 genre-2 genre-3 genre-4])
                               #"OT" [genre-1 (str "G" (case genre-2
                                                         "小" (Integer/parseInt genre-3)
                                                         "中" (+ 6 (Integer/parseInt genre-3))
                                                         "高" 10))]
                               [genre-1 genre-2 genre-3 genre-4])
                             (r/remove empty?)
                             (r/reduce (fn ;; Remove repeated categories.
                                         ([] [])
                                         ([a b] (if (= (peek a) b) a (conj a b)))))
                             (concat [(name-map corpus-name)])
                             (into []))}))
          (fn [] {}))
         metadata)

        bccwj-meta-annotation-data (utils/read-tsv-xz (io/resource "annotations-2013.tsv.xz") false)
        bccwj-annotations
        (let [header [:forward-or-afterward #_"前書きや後書きか"
                      :dialog #_"対話系か"
                      :quotation-type #_"引用系か"
                      :visual #_"視覚表現多用系（図表・イラスト・写真の多用）か"
                      :db-or-list #_"データベースやリスト系か"
                      :archaic #_"明治時代より以前の古い言葉，古い言い回しが多いか"
                      :foreign-language #_"外国語が多いか"
                      :math-or-code #_"数式やプログラミング言語などが多いか"
                      :legalese #_"法律文が多いか"
                      :questionable-content #_"教育上のぞましくなさそうか"
                      :low-content #_"そのほか，一定量の「本文」が認めがたいか"

                      :target-audience #_"対象とする読者"
                      :hard-soft #_"文章の硬軟"
                      :informality #_"くだけているか"
                      :addressing #_"文体(O)"

                      :_ #_"小説の主人公あるいは語り手の人称(H)"
                      :_ #_"小説の主人公あるいは語り手の人称(M)"
                      :_ #_"小説の主人公あるいは語り手の人称(S)"
                      :_ #_"小説の主人公あるいは語り手の人称(T)"
                      :_ #_"小説の主人公あるいは語り手の人称(N)"

                      :protagonist-personal-pronoun #_"小説の主人公あるいは語り手の人称"
                      :other-addressing #_"小説以外の文章の内容"]
              #_(->> bccwj-meta-annotation-data
                     first
                     rest)]
          (r/fold
           (r/monoid
            (fn [a [basename & record]]
              (assoc a basename
                     (for-map [[k v] (zipmap header record)
                               :when (not= k :_)]
                         k (cond (empty? v) nil

                                 ;; Coerce into Likert-scale Longs.
                                 (and (#{:target-audience :hard-soft :informality :addressing :other-addressing} k)
                                      (re-seq #"^\d+" v))
                                 (Long/parseLong v)

                                 ;; Coerce into binary values.
                                 (#{:forward-or-afterward :dialog
                                     :quotation-type :visual
                                     :db-or-list :archaic
                                     :foreign-language :math-or-code
                                     :legalese :questionable-content
                                     :low-content} k)
                                 (case (subs v 0 1) "1" true "0" false true)

                                 :else v))))
            (fn [] {}))
           (next bccwj-meta-annotation-data)))]

    (->> (merge-with merge bccwj-metadata bccwj-annotations)
         (map-vals (fn [vs] (into {} (r/remove #(nil? (val %)) vs))))
         vals)))

(comment
  (pprint (take 10 (map (comp :category :metadata) (filter #(= ((comp :subcorpus :metadata) %) "PM") (document-seq "/data/BCCWJ-2012-dvd1/DOC/" "/data/BCCWJ-2012-dvd1/C-XML/VARIABLE/")))))
  (def cats (into #{} (map (comp :category :metadata) (document-seq "/data/BCCWJ-2012-dvd1/DOC/" "/data/BCCWJ-2012-dvd1/C-XML/VARIABLE/"))))
  (def c-topics (into #{} (map :topic (mapcat (comp #(remove empty? %) #(filter map? %)) cats))))
  (def c-audiences (into #{} (map :audience (mapcat (comp #(remove empty? %) #(filter map? %)) cats))))
  (def c-medias (into #{} (map :media (mapcat (comp #(remove empty? %) #(filter map? %)) cats))))
  (def ndc-topics (into #{} (mapcat (comp #(remove empty? %) #(remove map? %)) cats)))
  (clojure.set/intersection c-topics ndc-topics)
  ;; How to merge these intersecting labels that are recorded at different levels?
  #{"情報科学" "宗教" "商業" "旅行" "物理学" "総記" "仏教" "キリスト教" "化学" "機械" "哲学" "ドイツ語" "家事" "法律" "数学" "日本語" "教育" "伝記" "社会" "生物学" "水産業"}
)

(s/defn parse-document :- SentencesSchema
  [filename :- s/Str]
  (->> filename
       io/input-stream
       xml/parse
       walk-and-emit
       (map #(hash-map ; Remove nils/empty strings from :tags and :sentences.
              :tags (into #{} (r/filter identity (:tags %)))
              :sentences (into [] (r/remove empty? (:sentences %)))))
       (remove #(empty? (:sentences %))) ; Remove paragraphs with no sentences.
       vec))

(s/defn document-seq :- [DocumentSchema] ;; TODO validate lazy-seq?
  [options :- {:corpus-dir s/Str
               :metadata-dir s/Str
               s/Keyword s/Any}]
  (lazy-seq
   (->> (parse-metadata (:metadata-dir options))
        (map (fn [metadata]
               (let [filename (str (:corpus-dir options) (:subcorpus metadata) "/" (:basename metadata) ".xml")]
                 {:metadata metadata
                  :paragraphs (parse-document filename)}))))))

(comment
  (s/with-fn-validation
   (take 10 (map (comp :category :metadata) (document-seq "/data/BCCWJ-2012-dvd1/DOC/" "/data/BCCWJ-2012-dvd1/C-XML/VARIABLE/")))))
