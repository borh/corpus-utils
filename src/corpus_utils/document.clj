(ns corpus-utils.document
  (:require [clojure.spec.alpha :as s]))

;; Required minimal metadata
(s/def :metadata/title string?)
(s/def :metadata/author string?)
(s/def :metadata/year int?)
(s/def :metadata/basename string?)
(s/def :metadata/category (s/coll-of string?))
(s/def :metadata/permission boolean?)

;; BCCWJ metadata
(s/def :metadata/gender #{:male :female :mixed})
(s/def :metadata/author-year int?)
(s/def :metadata/publisher string?)
(s/def :metadata/copyright string?)
(s/def :metadata/audience string?)
(s/def :metadata/media string?)
(s/def :metadata/topic string?)
(s/def :metadata/corpus string?)
(s/def :metadata/subcorpus string?)
(s/def :metadata/script string?)

;; BCCWJ extra annotations
(s/def :metadata/forward-or-afterward boolean?)
(s/def :metadata/dialog boolean?)
(s/def :metadata/quotation-type boolean?)
(s/def :metadata/visual boolean?)
(s/def :metadata/db-or-list boolean?)
(s/def :metadata/archaic boolean?)
(s/def :metadata/foreign-language boolean?)
(s/def :metadata/math-or-code boolean?)
(s/def :metadata/legalese boolean?)
(s/def :metadata/questionable-content boolean?)
(s/def :metadata/low-content boolean?)
(s/def :metadata/target-audience #{1 2 3 4 5})
(s/def :metadata/hard-soft #{1 2 3 4})
(s/def :metadata/informality #{1 2 3})
(s/def :metadata/addressing #{1 2 3})
(s/def :metadata/protagonist-personal-pronoun string?)
(s/def :metadata/other-addressing #{1 2 3 4})

(s/def :document/metadata
  (s/keys :req
          [:metadata/title :metadata/author :metadata/year :metadata/basename
           :metadata/category :metadata/permission]
          :opt
          [:metadata/gender :metadata/author-year :metadata/publisher :metadata/copyright
           :metadata/audience :metadata/media :metadata/topic
           :metadata/corpus :metadata/subcorpus :metadata/script

           :metadata/forward-or-afterward :metadata/dialog :metadata/quotation-type :metadata/visual
           :metadata/db-or-list :metadata/archaic :metadata/foreign-language :metadata/math-or-code
           :metadata/legalese :metadata/questionable-content :metadata/low-content :metadata/target-audience
           :metadata/hard-soft :metadata/informality :metadata/addressing :metadata/protagonist-personal-pronoun
           :metadata/other-addressing]))

(s/def :sentence/text string?)
(s/def :paragraph/sentences (s/coll-of :sentence/text))
(s/def :paragraph/tags (s/coll-of keyword?))
(s/def :document/paragraph (s/keys :req [:paragraph/tags :paragraph/sentences]))
(s/def :document/paragraphs (s/coll-of :document/paragraph))
(s/def :corpus/document (s/keys :req [:document/paragraphs]
                                ;; FIXME Optional :basename at top level for documents with external metadata.
                                :opt [:document/metadata :document/basename]))
(s/def :corpus/documents (s/coll-of :corpus/document))
