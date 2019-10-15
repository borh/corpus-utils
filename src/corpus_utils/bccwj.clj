(ns corpus-utils.bccwj
  (:require
    [fast-zip.core :as fz]
    [clojure.xml :as xml]
    [clojure.java.io :as io]
    [me.raynes.fs :as fs]
    [clojure.string :as string]
    [clojure.core.reducers :as r]
    ;;[org.httpkit.client :as http]
    [net.cgrand.xforms :as x]
    [clojure.spec.alpha :as s]
    [corpus-utils.ndc :as ndc]
    [corpus-utils.c-code :refer [c-code]]
    [corpus-utils.utils :as utils]
    [corpus-utils.document])
  (:import [fast_zip.core ZipperLocation]
           [java.io InputStream]))

;; # Importer for BCCWJ-Formatted C-XML Data
;;
;; Imports C-XML-type data.
;; M-XML is also supported, but is not recommended as we do our own parsing.

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
    :br                                                     ; Inline tag.
    :speech
    :speaker                                                ; For OM.
    :caption
    :citation
    :quotation
    :OCAnswer
    :OCQuestion})

(comment                                                    ; Not used at present. The text attr should not be used as a string in the sentence.
  (def in-sentence-tags
    #{:ruby
      :correction
      :missingCharacter
      :enclosedCharacter
      :er
      :cursive
      :image                                                ;; Inline tag (Emoji).
      :noteBodyInline                                       ;; Inline tag.
      :noteMarker                                           ;; Inline tag.
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

(defn backtrack-with-distance
  "Modified from `fast-zip.core/next` source. Like zip/next, but also keeps track of how far up the tree it goes."
  [loc]
  (loop [p loc
         depth 0]
    (if (and (not (identical? :end (.path p))) (fz/up p))
      (or (if-let [r (fz/right (fz/up p))] {:loc r :depth (inc depth)}) (recur (fz/up p) (inc depth)))
      {:loc   (ZipperLocation. (.ops loc) (.node loc) :end)
       :depth depth})))

(s/def ::loc #(instance? ZipperLocation %))
(s/def ::depth int?)
(s/fdef backtrack-with-distance
  :args (s/cat :loc ::loc)
  :ret (s/keys :req-un [::loc ::depth]))

;; FIXME break into emitter and consume-sequence-and-build-sentences functions; naming: next-direction conflates direction and depth

(defn walk-and-emit
  "Traverses xml-data (parsed with clojure.xml/parse or clojure.data.xml/parse) using a zipper and incrementally builds up and returns the document as a vector of maps (representing paragraphs), each element of which contains tags and a vector of sentences."
  [paragraph-level-tags xml-data]
  (loop [xml-loc (fz/xml-zip xml-data)
         tag-stack []
         par-loc (fz/down (fz/vector-zip [#:paragraph{:tags [] :sentences []}]))]
    (if (fz/end? xml-loc)
      (fz/root par-loc)
      (let [xml-node (fz/node xml-loc)
            tag (:tag xml-node)
            xml-loc-down (and (fz/branch? xml-loc) (fz/down xml-loc))
            xml-loc-right (and (not xml-loc-down) (fz/right xml-loc))
            xml-loc-up (and (not xml-loc-down) (not xml-loc-right) (backtrack-with-distance xml-loc)) ; At lowest depth for this paragraph.
            next-direction (cond xml-loc-down :down
                                 xml-loc-right :same
                                 :else (:depth xml-loc-up))
            coded-tag (if (#{:speech :speaker :title :titleBlock :orphanedTitle :list :caption :citation :quotation :OCAnswer :OCQuestion} tag)
                        (tag {:titleBlock :title :orphanedTitle :title} tag)
                        tag)
            new-tag-stack (case next-direction
                            :down (conj tag-stack coded-tag)
                            :same (if (= :br tag) tag-stack (conj (pop tag-stack) coded-tag)) ; :br is an exception as a paragraph break, as it does not increase XML tree depth
                            ((apply comp (repeat next-direction pop)) tag-stack))] ; Discard equal to up depth.
        #_(println next-direction tag coded-tag new-tag-stack)
        (recur
          (or xml-loc-down xml-loc-right (:loc xml-loc-up)) ; Same as (fz/next xml-loc).
          new-tag-stack
          (cond (paragraph-level-tags tag)                  ; Insert new paragraph, inserting the new tag stack.
                (-> par-loc (fz/insert-right {:paragraph/tags new-tag-stack :paragraph/sentences [""]}) fz/right)

                (= :sentence tag)                           ; Insert new sentence.
                (let [tag-stack (-> par-loc fz/node :tag-stack)]
                  ;; FIXME debug tag-stack
                  (fz/edit par-loc update-in [:paragraph/sentences] conj ""))

                (string? xml-node)                          ; Update last-inserted sentence's text.
                (fz/edit par-loc update-in [:paragraph/sentences (-> par-loc fz/node :paragraph/sentences count dec)] #(str % xml-node))

                :else par-loc))))))                         ; Do nothing.

(s/fdef walk-and-emit
  :args (s/cat :paragraph-level-tags set? :xml-data any?)
  :ret :document/paragraphs)

(defn parse-document
  [stream corpus]
  (let [p-tags (case corpus
                 "OY" (disj paragraph-level-tags :br)
                 paragraph-level-tags)
        document (->> stream
                      xml/parse
                      (walk-and-emit p-tags))]
    (into []
          (comp (map #(hash-map                             ; Remove nils/empty strings from :tags and :sentences.
                        :paragraph/tags (into #{} (filter identity) (:tags %))
                        :paragraph/sentences (into [] (remove empty?) (:paragraph/sentences %))))
                (remove #(empty? (:paragraph/sentences %)))) ; Remove paragraphs with no sentences.
          document)))

(s/fdef parse-document
  :args (s/cat :stream #(instance? InputStream %) :corpus string?)
  :ret :document/paragraphs)

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

(defn parse-metadata
  [metadata-dir]
  (let [metadata (utils/read-tsv (fs/file metadata-dir "Joined_info.txt") true)
        fixed-ndc (utils/read-tsv (fs/file metadata-dir "BCCWJ-NDC.txt") true)
        copyright (into {} (utils/read-tsv (fs/file metadata-dir "CopyRight_Annotation.txt") true))
        c-map (into {} (x/for [[k1 v1] (c-code "1")
                               [k2 v2] (c-code "2")
                               [k3 v3] (c-code "34")]
                              [(str k1 k2 k3) #:metadata{:audience v1 :media v2 :topic v3}]))
        name-map {"LB" "書籍"
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

        ndc-metadata
        (r/reduce
          (r/monoid
            (fn [m [ndc basename _ title subtitle number-volume _ publisher year isbn _
                    genre-1 genre-2 genre-3 genre-4 _ _
                    author author-year author-gender corpus-name]]
              ;; We replace the second genre field with the more informative ndc field. genre-1 is the top NDC category.
              ;; genre-3 is the c-code, and genre-4 is unused for the books corpus.
              (assoc m basename [genre-1 ndc genre-3 genre-4]))
            (fn [] {}))
          fixed-ndc)

        bccwj-metadata
        (r/reduce
          (r/monoid
            (fn [m
                 [basename _ title subtitle _ _ publisher year isbn _
                  genre-1 genre-2 genre-3 genre-4 _ _
                  author author-year author-gender corpus-name]]
              (let [metadata-patch? (if-let [v (get ndc-metadata basename)] v false)
                    [genre-1 genre-2 genre-3 genre-4] (if metadata-patch? metadata-patch? [genre-1 genre-2 genre-3 genre-4])]
                (assoc m basename
                         #:metadata{:permission  true
                                    :title       (str title (if-not (= "" (string/trim subtitle)) ": " subtitle))
                                    :author      author
                                    :gender      (let [authors (string/split author-gender #"/")]
                                                   (if (every? #(= (first authors) %) authors)
                                                     (case (first authors)
                                                       "男" :male
                                                       "女" :female
                                                       "" nil
                                                       :mixed)))
                                    :author-year (try (Integer/parseInt author-year) (catch Exception e))
                                    :publisher   (if (empty? publisher) "BCCWJ" publisher)
                                    :copyright   (copyright basename)
                                    :audience    (if (= (name-map corpus-name) "書籍") (:metadata/audience (c-map genre-3)))
                                    :media       (if (= (name-map corpus-name) "書籍") (:metadata/media (c-map genre-3)))
                                    :topic       (if (= (name-map corpus-name) "書籍") (:metadata/topic (c-map genre-3)))
                                    :year        (Integer/parseInt year)
                                    :basename    basename
                                    :corpus      "BCCWJ"
                                    :subcorpus   (name-map corpus-name)
                                    :category    (->> (condp re-seq corpus-name
                                                        #"(L|O|P)B" (->> [genre-1 genre-2]
                                                                         (r/remove #(or (= "なし" %)
                                                                                        (= "分類なし" %)))
                                                                         (r/mapcat #(ndc/ndc-map % [%]))
                                                                         (r/map #(string/replace % #"^\d\s" ""))
                                                                         (r/mapcat #(condp re-seq %

                                                                                      #"^(\d+)\.(\d+)$"
                                                                                      (let [[[_ top-level extra] & _] (re-seq #"^(\d+)\.(\d+)$" %)]
                                                                                        ;; We fallback on the top-level NDC concept if no direct match is found.
                                                                                        (get ndc/ndc-map top-level))

                                                                                      #"^[\d\s]+$"
                                                                                      (do (println "Removing unknown category: " basename genre-1 genre-2)
                                                                                          [nil])

                                                                                      [%]))
                                                                         (r/remove nil?))
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
                                                      (into []))})))
            (fn [] {}))
          metadata)

        bccwj-meta-annotation-data (utils/read-tsv-xz (io/resource "annotations-2013.tsv.xz") false)
        bccwj-annotations
        (let [header [:metadata/forward-or-afterward #_"前書きや後書きか"
                      :metadata/dialog #_"対話系か"
                      :metadata/quotation-type #_"引用系か"
                      :metadata/visual #_"視覚表現多用系（図表・イラスト・写真の多用）か"
                      :metadata/db-or-list #_"データベースやリスト系か"
                      :metadata/archaic #_"明治時代より以前の古い言葉，古い言い回しが多いか"
                      :metadata/foreign-language #_"外国語が多いか"
                      :metadata/math-or-code #_"数式やプログラミング言語などが多いか"
                      :metadata/legalese #_"法律文が多いか"
                      :metadata/questionable-content #_"教育上のぞましくなさそうか"
                      :metadata/low-content #_"そのほか，一定量の「本文」が認めがたいか"

                      :metadata/target-audience #_"対象とする読者"
                      :metadata/hard-soft #_"文章の硬軟"
                      :metadata/informality #_"くだけているか"
                      :metadata/addressing #_"文体(O)"

                      :_ #_"小説の主人公あるいは語り手の人称(H)"
                      :_ #_"小説の主人公あるいは語り手の人称(M)"
                      :_ #_"小説の主人公あるいは語り手の人称(S)"
                      :_ #_"小説の主人公あるいは語り手の人称(T)"
                      :_ #_"小説の主人公あるいは語り手の人称(N)"

                      :metadata/protagonist-personal-pronoun #_"小説の主人公あるいは語り手の人称"
                      :metadata/other-addressing #_"小説以外の文章の内容"]]
          (r/fold
            (r/monoid
              (fn [a [basename & record]]
                (assoc a basename
                         (x/into {} (x/for [[k v] (zipmap header record)
                                            :when (not= k :_)]
                                           [k
                                            (cond (empty? v) nil

                                                  ;; Coerce into Likert-scale Longs.
                                                  (and (#{:metadata/target-audience :metadata/hard-soft :metadata/informality
                                                          :metadata/addressing :metadata/other-addressing}
                                                        k)
                                                       (re-seq #"^\d+" v))
                                                  (Long/parseLong v)

                                                  ;; Coerce into binary values.
                                                  (#{:metadata/forward-or-afterward :metadata/dialog :metadata/quotation-type
                                                     :metadata/visual :metadata/db-or-list :metadata/archaic :metadata/foreign-language
                                                     :metadata/math-or-code :metadata/legalese :metadata/questionable-content
                                                     :metadata/low-content}
                                                   k)
                                                  (case (subs v 0 1) "1" true "0" false true)

                                                  :else v)]))))
              (fn [] {}))
            (next bccwj-meta-annotation-data)))]
    (into {} (x/by-key (map (fn [vs] (into {} (r/remove #(nil? (val %)) vs)))))
          (merge-with merge bccwj-metadata bccwj-annotations))))

(s/fdef parse-metadata
  :args (s/cat :metadata-dir string?)
  :ret (s/map-of :metadata/basename :document/metadata))

(defn document-seq
  [options]
  (let [metadata (parse-metadata (:metadata-dir options))]
    (utils/walk-zip-file
      (fn [basename text]
        (if-let [meta (get metadata basename)]
          #:document{:metadata   meta
                     :paragraphs (parse-document text (:metadata/subcorpus meta))}
          (throw (ex-info "Missing metadata for BCCWJ basename" {:basename basename}))))
      (:corpus-dir options))))

(s/fdef document-seq
  :args (s/cat :options (s/keys :req-un [::corpus-dir string? ::metadata-dir string?]))
  :ret :corpus/documents)
