(defproject corpus-utils "0.1.3.1"
  :description "Miscellaneous utilities to parse Japanese language corpora with Clojure"
  :url "https://github.com/borh/corpus-utils.git"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha4"]

                 [clj-mecab "0.4.1"]

                 [fast-zip "0.5.2"]
                 [org.clojure/data.csv "0.1.2"]
                 [me.raynes/fs "1.4.6"]
                 [org.apache.commons/commons-compress "1.9"]
                 [org.tukaani/xz "1.5"]

                 [prismatic/schema "0.3.3"]
                 [prismatic/plumbing "0.3.5"]]
  :min-lein-version "2.0.0"
  :resource-paths ["data"]
  :jvm-opts ["-server"]
  :main ^{:skip-aot true} corpus-utils.text)
