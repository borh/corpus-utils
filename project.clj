(defproject corpus-utils "0.1.10"
  :description "Miscellaneous utilities to parse Japanese language corpora with Clojure"
  :url "https://github.com/borh/corpus-utils.git"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]

                 [clj-mecab "0.4.2"]

                 [fast-zip "0.6.1" :exclusions [com.cemerick/austin]]
                 [org.clojure/data.csv "0.1.3"]
                 [me.raynes/fs "1.4.6"]
                 [org.apache.commons/commons-compress "1.10"]
                 [org.tukaani/xz "1.5"]
                 [prismatic/schema "1.0.3"]
                 [prismatic/plumbing "0.5.2"]]
  :min-lein-version "2.0.0"
  :resource-paths ["data"]
  :jvm-opts ["-server"]
  :main ^:skip-aot corpus-utils.text)
