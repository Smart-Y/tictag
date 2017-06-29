(ns cards.core
  (:require [re-frame.core :as re-frame]
            [tictag.views :as v]
            [devtools.core :as devtools]
            [devcards.core :as dc]
            [tictag.views.settings :as vs]
            [c2.ticks]
            [c2.scale]
            [cljs.test :refer [is testing async]]
            [reagent.core :as reagent]
            [taoensso.timbre :as timbre])
  (:require-macros [devcards.core :refer [defcard-rg deftest]]))

(defonce runonce
  [(devtools/install! [:formatters :hints :async])
   (enable-console-print!)])

(def Y (c2.scale/linear :domain [0 1]
                        :range [800 0]))
(def X (c2.scale/linear :domain [0 1]
                        :range [0 800]))

(defn circle [{xscale :x yscale :y} x y]
  [:circle {:cx    (xscale x)
            :cy    (yscale y)
            :r     3
            :fille "black"}])

(defcard-rg axis-left
  [:svg {:width 800 :height 800}
   (let [{yticks :ticks} (c2.ticks/search (:domain Y))
         {xticks :ticks} (c2.ticks/search (:domain X))]
     [:g {:stroke-width 1 :stroke "black"}
      (c2.svg/axis Y yticks :orientation :left)
      [:g {:transform "translate(0, 800)"}
       (c2.svg/axis X xticks :text-margin -9 :orientation :bottom)]
      (for [x (take 1000 (repeatedly rand))
            :let [y (rand x)]]
        (circle {:x X :y Y} x y))])])


(defcard-rg slack-help
  [vs/slack-help])

(defcard-rg slack-preferences
  (let [state (reagent/atom {})]
    [vs/slack-preferences-component
     (fn [[ev param]]
       (timbre/debug "Received event: " ev param)
       (case ev
         :slack/want-dm? (swap! state assoc :dm? param)
         :slack/want-channel? (swap! state assoc :channel? param)
         :slack/channel (swap! state assoc :channel param)))
     state]))


