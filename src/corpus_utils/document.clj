(ns corpus-utils.document
  (:require [schema.core :as s]))

(def opt s/optional-key)

(def BCCWJAnnotationSchema
  {(opt :forward-or-afterward) Boolean
   (opt :dialog)               Boolean
   (opt :quotation-type)       Boolean
   (opt :visual)               Boolean
   (opt :db-or-list)           Boolean
   (opt :archaic)              Boolean
   (opt :foreign-language)     Boolean
   (opt :math-or-code)         Boolean
   (opt :legalese)             Boolean
   (opt :questionable-content) Boolean
   (opt :low-content)          Boolean
   (opt :target-audience)      (s/enum 1 2 3 4 5)
   (opt :hard-soft)            (s/enum 1 2 3 4)
   (opt :informality)          (s/enum 1 2 3)
   (opt :addressing)           (s/enum 1 2 3)
   (opt :protagonist-personal-pronoun) s/Str
   (opt :other-addressing)     (s/enum 1 2 3 4)})

(def MetadataSchema
  "Schema of a document's possible metadata"
  (merge BCCWJAnnotationSchema
         {:title     s/Str
          :author    s/Str
          (opt :gender) (s/enum :male :female :mixed)
          (opt :author-year) s/Int
          :publisher s/Str
          (opt :copyright) s/Str
          (opt :audience) s/Str
          (opt :media) s/Str
          (opt :topic) s/Str
          :year      s/Int
          :basename  s/Str
          :corpus    s/Str
          (opt :subcorpus) s/Str
          :category  [s/Str]}))

(def SentencesSchema
  [{:tags clojure.lang.PersistentHashSet
    :sentences [s/Str]}])

(def DocumentSchema
  {:paragraphs SentencesSchema
   :metadata   MetadataSchema})

(def UnidicMorphemeSchema
  {:pos-1 s/Str
   :pos-2 s/Str
   :pos-3 s/Str
   :pos-4 s/Str
   :c-type s/Str
   :c-form s/Str
   :l-form s/Str
   :lemma s/Str
   :orth s/Str
   :pron s/Str
   :orth-base s/Str
   :pron-base s/Str
   :goshu s/Str
   :i-type s/Str
   :i-form s/Str
   :f-type s/Str
   :f-form s/Str})
