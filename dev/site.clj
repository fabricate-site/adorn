(ns site
  "Namespace for building Adorn's documentation."
  (:require [dev.onionpancakes.chassis.core :as c]
            [clojure.walk :as walk]
            [site.fabricate.adorn :as adorn]
            [site.functions :as site-fns]
            [cybermonday.core :as md]))

(def out-dir "dev/html/")

(def imports
  (list
   [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link
    {:rel "preconnect" :crossorigin true :href "https://fonts.gstatic.com"}]
   [:link
    {:rel "stylesheet"
     :href
     "https://fonts.googleapis.com/css2?family=Eczar:wght@400..800&family=Newsreader:ital,opsz,wght@0,6..72,200..800;1,6..72,200..800&family=Recursive:slnt,wght,CASL,CRSV,MONO@-15..0,300..1000,0..1,0..1,0..1&display=swap"}]
   [:link
    {:href "https://unpkg.com/normalize.css@8.0.1/normalize.css"
     :rel  "stylesheet"}]
   [:link {:rel "stylesheet" :href "/main.css"}]))

(defn convert-pre
  [[tag attrs pre-contents :as pre]]
  (if (and (string? pre-contents) (re-find #"language-clojure" pre-contents))
    [tag attrs (adorn/clj->hiccup pre-contents)]
    pre))

(defn walk-hiccup [h] (walk/postwalk convert-pre h))

(defn md-page
  [{:keys [body frontmatter] :as data}]
  [c/doctype-html5
   [:html
    [:head [:title (:title frontmatter "Adorn")] imports
     [:body [:main [:article (:body data)]]]]]])

(defn convert-pre
  [[tag attrs pre-contents :as pre]]
  (if (= "clojure" (:language attrs))
    [:pre [:code {:class "language-clojure"} (adorn/clj->hiccup pre-contents)]]
    [:pre [:code {:class (str "language-" (:language attrs))}] pre-contents]))

(defn convert-task-list
  [[tag attrs [_ _attrs & contents]]]
  [:li [:input {:disabled true :checked (:checked? attrs) :type "checkbox"}] " "
   (apply list contents)])

(defn unparagraph-img
  "if the given element is a paragraph containing only a single image,
  return the image without the enclosing paragraph"
  [i]
  (if
      (and (vector? i) (= :p (first i)) (= 3 (count i)) (= :img (get-in i [2 0])))
    (peek i)
    i))

(comment
  (unparagraph-img [:p {}
                    [:img
                     {:src   "/logo-transparent.svg"
                      :alt   "<img src=\"dev/logo.svg\">"
                      :title nil}]]))




(def pages
  {"readme.html"      (md-page
                       (md/parse-md
                        (slurp "README.md")
                        {:lower-fns {:markdown/fenced-code-block convert-pre
                                     :markdown/task-list-item convert-task-list
                                     :p unparagraph-img}}))
   "performance.html" (md-page
                       (md/parse-md
                        (slurp "notes/performance.md")
                        {:lower-fns {:markdown/fenced-code-block convert-pre
                                     :markdown/task-list-item convert-task-list
                                     :p unparagraph-img}}))
   "demos.html"
   ;; look at how easy this is
   [c/doctype-html5
    [:html [:head [:title "Adorn: code formatting demos"] imports]
     [:body
      [:main
       (into [:article]
             (map site-fns/process-form
                  (adorn/clj->hiccup (slurp "dev/site/demos.clj"))))]]]]})


(defn build!
  [{:keys [pages] :or {pages pages} :as opts}]
  (doseq [[out-path page-data] pages]
    (spit (str out-dir out-path) (c/html page-data))))



(comment
  (md-line "")
  (md/parse-body "")
  (md/parse-md (slurp "notes/performance.md"))
  (md/parse-body (slurp "README.md"))
  (build! {}))
