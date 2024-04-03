(ns site
  "Namespace for building Adorn's documentation."
  (:require [dev.onionpancakes.chassis.core :as c]
            [cybermonday.core :as md]))

(def out-dir "dev/html/")

(def imports
  (list
   [:link
    {:href "https://unpkg.com/normalize.css@8.0.1/normalize.css"
     :rel  "stylesheet"}]
   [:link {:rel "stylesheet" :href "/main.css"}]
   [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link
    {:rel "preconnect" :crossorigin true :href "https://fonts.gstatic.com"}]
   [:link
    {:rel "preconnect"
     :href
     "https://fonts.googleapis.com/css2?family=Alegreya:ital,wght@0,400..900;1,400..900&family=Recursive:slnt,wght,CASL,CRSV,MONO@-15..0,300..1000,0..1,0..1,0..1&display=swap"}]))


(defn md-page
  [{:keys [body front-matter] :as data}]
  [c/doctype-html5
   [:html
    [:head
     [:title "Adorn: extensible generation of Hiccup forms from Clojure code"]
     imports [:body [:main [:article (:body data)]]]]]])


(def pages {"intro.html" (md-page (md/parse-md (slurp "README.md")))})

(defn build!
  [{:keys [pages] :or {pages pages} :as opts}]
  (doseq [[out-path page-data] pages]
    (spit (str out-dir out-path) (c/html page-data))))

(comment
  (build! {}))
