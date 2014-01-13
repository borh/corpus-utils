(ns corpus-utils.kokken
  (:require [clojure.zip :as z]
            [clojure.xml :as xml]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [schema.core :as s]
            [schema.macros :as sm]
            [corpus-utils.document :refer [DocumentSchema]]))

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

(defn- backtrack-with-distance
  "Modified from `clojure.zip/next` source. Like zip/next, but also keeps track of how far up the tree it goes."
  [loc]
  (loop [p loc
         depth 0]
    (if (z/up p)
      (or (if-let [r (z/right (z/up p))] [r (inc depth)]) (recur (z/up p) (inc depth)))
      [[(z/node p) :end] depth])))

#_(defn walk-and-emit
  "Traverses xml-data (parsed with clojure.xml/parse or clojure.data.xml/parse) using a zipper and incrementally builds up and returns the document as a vector of maps (representing paragraphs), each element of which contains tags and a vector of sentences."
  [xml-data]
  (loop [xml-loc (z/xml-zip xml-data)
         tag-stack []
         par-loc (z/down (z/vector-zip [{:tags [] :sentences []}]))]
    (if (z/end? xml-loc)
      (z/root par-loc)
      (let [xml-node (z/node xml-loc)
            tag (:tag xml-node)
            xml-loc-down  (and (z/branch? xml-loc) (z/down xml-loc))
            xml-loc-right (and (not xml-loc-down) (z/right xml-loc))
            xml-loc-up    (and (not xml-loc-down) (not xml-loc-right) (backtrack-with-distance xml-loc)) ; At lowest depth for this paragraph.
            next-direction (cond xml-loc-down :down
                                 xml-loc-right :same
                                 :else (second xml-loc-up))
            coded-tag (if (#{:speech :speaker :title :titleBlock :orphanedTitle :list :caption :citation :quotation :OCAnswer :OCQuestion} tag)
                        (tag {:titleBlock :title :orphanedTitle :title} tag))
            new-tag-stack (case next-direction
                            :down (conj tag-stack coded-tag)
                            :same (if (= :br tag) tag-stack (conj (pop tag-stack) coded-tag)) ; :br is an exception as a paragraph break, as it does not increase XML tree depth
                            ((apply comp (repeat next-direction pop)) tag-stack))] ; Discard equal to up depth.
        (recur
         (or xml-loc-down xml-loc-right (first xml-loc-up)) ; Same as (z/next xml-loc).
         new-tag-stack
         (cond (paragraph-level-tags tag) ; Insert new paragraph, inserting the new tag stack.
               (-> par-loc (z/insert-right {:tags new-tag-stack :sentences [""]}) z/right)

               (= :s tag) ; Insert new sentence.
               (let [tag-stack (-> par-loc z/node :tag-stack)]
                 (-> par-loc (z/edit update-in [:sentences] conj "")))

               (string? xml-node) ; Update last-inserted sentence's text.
               (-> par-loc (z/edit update-in [:sentences (-> par-loc z/node :sentences count dec)] #(str % xml-node)))

               :else par-loc)))))) ; Do nothing.

(defn parse;; :- [DocumentSchema]
  "Each XML file from The Sun corpus contains several articles, so we return a vector of documents."
  [filename;; :- s/Str
  ]
  (let [root-loc (-> filename
                     io/input-stream
                     xml/parse
                     z/xml-zip)
        [corpus year number] ((juxt :雑誌名 :年 :号) (-> root-loc z/node :attrs ))]
    (->> root-loc
         z/down ;; Descend to the :記事 level.
         (iterate z/right) ;; FIXME broken
         (take 1)
         ;; Documents can be empty...
         (remove nil?)
         #_(map (fn [article-loc]
                (let [[title author title-alt style genre] ((juxt :題名 :著者 :欄名 :文体 :ジャンル) (:attrs (z/node article-loc)))]
                  {:paragraphs
                   #_:TODO (->> article-loc
                                z/down
                                (iterate z/right)
                                (remove nil?)
                                (partition-by #(if (= (:tag (z/node %)) :br) true))
                                (map :content))
                   :metadata
                   {:title     title
                    :author    author
                    :publisher corpus
                    :year      year
                    :basename  (fs/base-name filename ".xml")
                    :corpus    corpus
                    :genre     [corpus genre]}}))))))

(sm/defn document-seq
  [data-dir :- s/Str]
  (->> (fs/glob data-dir "/*.xml")
       (map parse)))

;;(parse "/data/taiyo-corpus/XML/t189506.xml")

(comment
  (sm/with-fn-validation (parse "/data/taiyo-corpus/XML/t189506.xml")))
