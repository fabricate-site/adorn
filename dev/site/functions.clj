(ns site.functions
  (:require [clojure.string :as str]
            [site.fabricate.adorn :as adorn]
            [site.fabricate.adorn.forms :as forms]
            [cybermonday.core :as md]))

(defn md-line
  [md-str]
  (if (empty? md-str) md-str (subvec (get (md/parse-body md-str) 2) 2)))

(def comment-line-pattern #"(?ms)^(?:\s*;+\s*)(.*)\z")

(defn comment->paragraph
  [[_tag _attrs _start & contents]]
  ;; don't worry about linebreaks for now
  (->> contents
       (mapcat (fn [line]
                 (let [[_txt trimmed] (re-matches comment-line-pattern line)]
                   (md-line trimmed))))
       (reduce conj [:p {:class "comment-paragraph"}])))

(defn process-form
  [f]
  (if (and (vector? f) (re-find #"comment" (get-in f [1 :class])))
    (comment->paragraph f)
    f))
