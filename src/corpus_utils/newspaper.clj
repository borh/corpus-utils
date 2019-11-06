(ns corpus-utils.newspaper
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [corpus-utils.text :as text]
            [corpus-utils.utils :as utils]
            [me.raynes.fs :as fs]
            [parallel.xf :as xf]
            [clojure.string :as string])
  (:import [com.ibm.icu.text Transliterator]))

;; Mainichi Newspaper format corpus reader.
;; http://www.nichigai.co.jp/sales/pdf/man_mai.pdf
;; http://www.nichigai.co.jp/sales/pdf/man_yomi_j.pdf

(defonce full-to-halfwidth (Transliterator/getInstance "Fullwidth-Halfwidth"))
(defn convert-full-to-halfwidth
  [^String s]
  (.transliterate ^Transliterator full-to-halfwidth s))

(def mai-category-map
  {"０１" "1面"
   "０２" "2面"
   "０３" "3面"
   "０４" "解説"
   "０５" "社説"
   "０７" "国際"
   "０８" "経済"
   "１０" "特集"
   "１２" "総合"
   "１３" "家庭"
   "１４" "文化"
   "１５" "読書"
   "１６" "科学"
   "１７" "生活"
   "１８" "芸能"
   "３５" "スポーツ"
   "４１" "社会"})

(def y6-category-map
  ;; http://www.nichigai.co.jp/sales/pdf/man_yomi_j.pdf
  ;; ＶＯ４ＯＯＯＯ
  {"Ｑ０１" "皇室"
   "Ｒ０１" "国際"
   "Ｒ０２" "アジア 太平洋"
   ;; "ＲＯ２" "アジア 太平洋"                                          ;; Mistake in corpus.
   "Ｒ０３" "南北アメリカ"
   "Ｒ０４" "西欧"
   "Ｒ０５" "旧ソ連・東欧"
   "Ｒ０６" "中東"
   "Ｒ０７" "アフリカ"
   "Ｓ０１" "科学"
   "Ｓ０２" "宇宙"
   "Ｓ０３" "地球"
   "Ｓ０４" "理工学"
   "Ｓ０５" "生命工学"
   "Ｓ０６" "動植物"
   ;;  "ＳＯ６" "動植物"                                              ;; Mistake in corpus.
   "Ｔ０１" "犯罪・事件"
   ;;  "ＴＯ１" "犯罪・事件"                                            ;; Mistake in corpus.
   "Ｔ０２" "事故"
   ;;  "ＴＯ２" "事故"                                               ;; Mistake in corpus
   "Ｔ０３" "災害"
   "Ｕ０１" "生活"
   "Ｕ０２" "健康"
   "Ｕ０３" "衣"
   "Ｕ０４" "食"
   "Ｕ０５" "住"
   "Ｕ０６" "余暇"
   "Ｕ０７" "行事"
   "Ｖ０１" "文化"
   "Ｖ０２" "学術"
   "Ｖ０３" "美術"
   "Ｖ０４" "映像"
   "Ｖ０５" "文学"
   "Ｖ０６" "音楽"
   "Ｖ０７" "演劇"
   "Ｖ０８" "芸能"
   "Ｖ０９" "舞踊"
   "Ｖ１０" "宗教"
   "Ｗ０１" "スポーツ"
   "Ｗ０２" "巨人軍"
   "Ｘ０１" "社会"
   ;;  "ＸＯ１" "社会"                                               ;; Mistake in corpus.
   "Ｘ０２" "市民運動"
   "Ｘ０３" "社会保障"
   "Ｘ０４" "環境"
   "Ｘ０５" "婦人"
   ;;  "ＸＯ５" "婦人"                                               ;; Mistake in corpus.
   "Ｘ０６" "子供"
   "Ｘ０７" "中高年"
   "Ｘ０８" "勲章・賞"
   "Ｘ０９" "労働"
   "Ｘ１０" "教育"
   "Ｙ０１" "経済"
   "Ｙ０２" "財政"
   "Ｙ０３" "金融"
   "Ｙ０４" "企業"
   ;;  "ＹＯ４" "企業"                                               ;; Mistake in corpus.
   "Ｙ０５" "中小企業"
   "Ｙ０６" "技術"
   "Ｙ０７" "情報"
   ;;  "ＹＯ７" "情報"                                               ;; Mistake in corpus.
   "Ｙ０８" "サービス"
   ;;  "ＹＯ８" "サービス"                                             ;; Mistake in corpus.
   "Ｙ０９" "貿易"
   "Ｙ１０" "国土・都市計画"
   "Ｙ１１" "鉱工業"
   "Ｙ１２" "資源・エネルギー"
   "Ｙ１３" "農林水産"
   "Ｚ０１" "政治"
   ;;  "ＺＯ１" "政治"                                               ;; Mistake in corpus.
   "Ｚ０２" "右翼・左翼"
   "Ｚ０３" "選挙"
   "Ｚ０４" "行政"
   ;;  "ＺＯ４" "行政"                                               ;; Mistake in corpus.
   "Ｚ０５" "地方自治"
   "Ｚ０６" "司法"
   "Ｚ０７" "警察"
   "Ｚ０８" "日本外交"
   ;;  "ＺＯ８" "日本外交"                                             ;; Mistake in corpus.
   "Ｚ０９" "軍事"
   "Ｚ１０" "戦争"
   ;; FIXME
   ""    "不明"})

(def !x (atom {}))

#_{"Ｖ０Ｒ" 1,
   "Ｖ１３" 1,
   "ＸＯ４" 1,
   "ＸＸ０" 1,
   "Ｙ３"  1,
   "Ｙ０Ｙ" 1,
   "Ｚ１４" 1,
   "Ｕ"   1,
   "Ｙ６０" 1,
   "０Ｚ３" 1,
   "ＦＣ"  2,
   "Ｕ０９" 1,
   "ＦＥ"  1,
   "Ｙ８"  1,
   "０２Ｒ" 1,
   "Ｘ１１" 1,
   "Ｔ１Ｚ" 1,
   "Ｔ"   1,
   "Ｚ９５" 2,
   "Ｓ１３" 2,
   "Ｏ０４" 2,
   "Ｐ０４" 2,
   "Ｔ１"  1,
   "ＲＧＧ" 1,
   "０Ｙ４" 1,
   "ＸＹ０" 1,
   "Ｔ０Ｔ" 1,
   "Ｔ０６" 4,
   "Ｐ４４" 1,
   "Ｐ０２" 2,
   "０"   1,
   "１"   1,
   "Ｔ１０" 5,
   "Ｚ１"  1,
   "０６"  3,
   "Ｖ９１" 1,
   "３"   2,
   "Ｕ０Ｕ" 1,
   "ＶＯ４" 1,
   "Ｗ０４" 1,
   "Ｕ７"  1,
   "ＺＯ５" 7,
   "ＵＯ２" 1,
   "Ｕ０８" 6,
   "Ｔ９１" 1,
   "Ｒ０９" 3,
   "ＷＯ１" 3,
   "Ｚ８Ｒ" 1,
   "３Ｙ１" 1,
   "Ｔ０４" 3,
   "Ｗ１０" 4,
   "Ｗ０５" 1,
   "０Ｚ５" 2,
   "Ｔ１３" 2,
   "Ｐ０３" 1,
   "Ｕ０"  1,
   "Ｔ０５" 1,
   "Ｔ１１" 1,
   "Ｕ６０" 1,
   "Ｔ０７" 4,
   "ＶＯ２" 1,
   "Ｙ１４" 1,
   "Ｒ９４" 1,
   "ＳＯ３" 2,
   "ＲＯ６" 3,
   "Ｐ０１" 1,
   "ＶＯ６" 1,
   "Ｔ００" 1,
   "ＴＯ３" 3,
   "Ｚ４０" 1,
   "ＹＯ２" 1,
   "Ｔ０８" 1,
   "ＶＯ１" 1,
   "８Ｙ０" 1,
   "ＷＲ０" 1,
   "Ｘ９１" 1,
   "Ｙ９４" 1,
   "７１０" 1,
   "ＸＯ６" 1}

(defn process-doc [tagged-lines]
  (reduce
    (fn [m [tag s]]
      (if (= s "【現在著作権交渉中の為、本文は表示できません】")
        (reduced m)
        (condp contains? tag
          ;; First match corresponds to codes in newest format, second for Mainichi <= 1993.
          #{"ＡＤ" "ＭＥ" "Ｙ６"}
          (let [category-vec (reduce (fn                    ;; Remove repeated categories.
                                       ([] [])
                                       ([a b] (if (= (peek a) b) a (conj a b))))
                                     []
                                     (into [] (remove nil?)
                                           (into ((juxt mai-category-map y6-category-map) s)
                                                 ((juxt mai-category-map y6-category-map) (string/replace s #"０" "Ｏ")))))]
            (if (empty? category-vec)
              (do
                (swap! !x update s (fnil inc 0))
                (println (format "Unknown newspaper category '%s' in map '%s'" s m))
                m)
              (do
                (assert (= 1 (count category-vec)))
                (update-in m [:document/metadata :metadata/category]
                           conj
                           (first category-vec)))))

          #{"Ｃ０" "ＡＦ"}
          (-> m
              (assoc-in [:document/metadata :metadata/basename] (convert-full-to-halfwidth s))
              (assoc-in [:document/metadata :metadata/year]
                        (let [year-fragment
                              (convert-full-to-halfwidth (if (== (count s) 9)
                                                           (subs s 0 2)
                                                           (subs s 2 4)))]
                          (Integer/parseInt
                            (if (= \9 (first year-fragment))
                              (str "19" year-fragment)
                              (str "20" year-fragment))))))

          #{"Ｔ１" "ＴＩＮ"}
          (assoc-in m [:document/metadata :metadata/title] s)

          #{"Ｔ２" "ＨＯＮ"}
          (update m :document/paragraphs
                  (fn [paragraphs]
                    (conj paragraphs
                          #:paragraph{:tags      #{}
                                      :sentences (text/split-japanese-sentence s)})))
          m)))
    #:document{:paragraphs []
               :metadata   #:metadata{:author "" :title "" :permission false :category ["新聞"]}}
    tagged-lines))

(s/fdef process-doc
  :args (s/cat :kv-seq (s/coll-of (s/tuple string? #_#{"ＡＤ" "ＭＥ" "Ｃ０" "ＡＦ" "Ｔ１" "ＴＩＮ" "Ｔ２" "ＨＯＮ"} string?)))
  :ret :corpus/document)

(defn doc-start? [s]
  (if (= (subs s 0 4) "＼ＩＤ＼")
    true))

(defn split-tags [s]
  (->> s
       (re-seq #"^＼([^＼]+)＼(.+)$")
       first
       rest
       vec))

(defn document-seq
  [{:keys [corpus-dir]}]
  (let [corpus-cache-dir (utils/hash-file-path corpus-dir)]
    (if (fs/exists? corpus-cache-dir)
      (map utils/read-cached-file (fs/glob corpus-cache-dir "*.transit"))
      (do (fs/mkdirs corpus-cache-dir)
          (mapcat
            (fn [corpus-file]
              (let [lines (line-seq (io/reader corpus-file))]
                (sequence (comp (partition-by doc-start?)
                                (partition-all 2)
                                (map (fn [x] (mapcat identity x)))
                                (map (fn [doc]
                                       (let [d (into []
                                                     (comp (map split-tags)
                                                           (filter not-empty))
                                                     doc)]
                                         d)))
                                (#_xf/pmap map process-doc)
                                (remove #(empty? (:document/paragraphs %)))
                                (map (fn [doc]
                                       (future (utils/cache-processed-file! corpus-cache-dir (:metadata/basename (:document/metadata doc)) doc))
                                       doc)))
                          lines)))
            (mapcat identity
                    (fs/walk
                      (fn [root dirs files]
                        (for [file files]
                          (if (= ".txt" (fs/extension file))
                            (fs/file root file))))
                      corpus-dir)))))))

(s/fdef document-seq
  :args (s/cat :options (s/keys :req-un [::corpus-dir string?]))
  :ret :corpus/documents)