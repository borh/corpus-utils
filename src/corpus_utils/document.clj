(ns corpus-utils.document
  (:require [clojure.core.typed :as t]
            [schema.core :as s]
            [schema.macros :as sm]))

(t/def-alias Gender
  "Author gender. In the case of multiple authors with male and female members, we assign a :mixed value."
  (U ':male ':female ':mixed))

(t/def-alias Metadata "Metadata"
  (HMap :mandatory {:title     String
                    :author    String
                    :publisher String
                    :year      Number
                    :basename  String
                    :corpus    String
                    :category  (t/Vec String)}
        :optional {:gender Gender
                   :author-year  Number
                   :distribution String ;; -> 月刊/週刊...
                   :copyright    String
                   :subcorpus    String
                   :subcorpus-ja String}
        :complete? true))

(t/def-alias Document "Document"
  (U Metadata
     (HMap :mandatory {:tags (t/Set String) ;; FIXME What are tags here?
                       :sentences (t/Vec String)}
           :complete? true)))

(t/def-alias UnidicMorpheme "UnidicMorpheme"
  (HMap :mandatory {:pos-1 String
                    :pos-2 String
                    :pos-3 String
                    :pos-4 String
                    :c-type String
                    :c-form String
                    :l-form String
                    :lemma String
                    :orth String
                    :pron String
                    :orth-base String
                    :pron-base String
                    :goshu String}
        :optional {:i-type String
                   :i-form String
                   :f-type String
                   :f-form String}
        :complete? true))

(def BCCWJAnnotationSchema
  {(s/optional-key :forward-or-afterward) Boolean
   (s/optional-key :dialog)               Boolean
   (s/optional-key :quotation-type)       Boolean
   (s/optional-key :visual)               Boolean
   (s/optional-key :db-or-list)           Boolean
   (s/optional-key :archaic)              Boolean
   (s/optional-key :foreign-language)     Boolean
   (s/optional-key :math-or-code)         Boolean
   (s/optional-key :legalese)             Boolean
   (s/optional-key :questionable-content) Boolean
   (s/optional-key :low-content)          Boolean
   (s/optional-key :target-audience)      (s/enum 1 2 3 4 5)
   (s/optional-key :hard-soft)            (s/enum 1 2 3 4)
   (s/optional-key :informality)          (s/enum 1 2 3)
   (s/optional-key :addressing)           (s/enum 1 2 3)
   (s/optional-key :protagonist-personal-pronoun) s/Str
   (s/optional-key :other-addressing)     (s/enum 1 2 3 4)})

(def MetadataSchema
  "Schema of a document's possible metadata"
  (merge BCCWJAnnotationSchema
         {:title     s/Str
          :author    s/Str
          (s/optional-key :gender) (s/enum :male :female :mixed)
          (s/optional-key :author-year) s/Int
          :publisher s/Str
          (s/optional-key :copyright) s/Str
          (s/optional-key :audience) s/Str
          (s/optional-key :media) s/Str
          (s/optional-key :topic) s/Str
          :year      s/Int
          :basename  s/Str
          :corpus    s/Str
          :subcorpus s/Str
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

(comment
  (t/check-ns))
