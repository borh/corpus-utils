(ns corpus-utils.text-test
  (:require [clojure.test :refer :all]
            [corpus-utils.text :refer :all]))

(deftest test-lines->paragraph-sentences
  (testing "nil and empty string handling."
    (is (= [] (lines->paragraph-sentences ["" nil "" ""]))))
  (testing "sentence and paragraph brake handling."
    (is (= [["フェイスブック（ＦＢ）やツイッターなどソーシャルメディアを使った採用活動が、多くの企業に広がっている。"
             "ＦＢでの会社説明会やＯＢ・ＯＧ訪問受け付け、ソーシャルスキルをはかって面接代わりにする動きも出てきた。"
             "企業側のソーシャル活用法も多様になっている。"]
            ["「実際、どれくらいの休みが取れるのでしょうか」「女性にとって働きやすい職場ですか」。"]]
           (lines->paragraph-sentences
            ["フェイスブック（ＦＢ）やツイッターなどソーシャルメディアを使った採用活動が、多くの企業に広がっている。ＦＢでの会社説明会やＯＢ・ＯＧ訪問受け付け、ソーシャルスキルをはかって面接代わりにする動きも出てきた。"
             "企業側のソーシャル活用法も多様になっている。"
             nil
             "「実際、どれくらいの休みが取れるのでしょうか」「女性にとって働きやすい職場ですか」。"])))))

(comment
  (bench (lines->paragraph-sentences ["フェイスブック（ＦＢ）やツイッターなどソーシャルメディアを使った採用活動が、多くの企業に広がっている。ＦＢでの会社説明会やＯＢ・ＯＧ訪問受け付け、ソーシャルスキルをはかって面接代わりにする動きも出てきた。" "企業側のソーシャル活用法も多様になっている。" nil "「実際、どれくらいの休みが取れるのでしょうか」「女性にとって働きやすい職場ですか」。"])))
