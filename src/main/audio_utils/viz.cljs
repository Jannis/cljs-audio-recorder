(ns audio-utils.viz
  (:require [sablono.core :as sab :refer-macros [html]]
            [thi.ng.geom.viz.core :as viz]
            [thi.ng.geom.svg.core :as svg]))


(defn linear-distribution
  [domain n]
  (mapv #((viz/linear-scale [0 n] domain) %) (range 0 n)))

(defn plot-data
  [n buffer width height]
  {:x-axis (viz/linear-axis
            {:domain      [0 n]
             :range       [50 (- width 10)]
             :major       100
             :minor       10
             :pos         (- height 50)})
   :y-axis (viz/linear-axis
            {:domain      [-1.0 1.0]
             :range       [(- height 50) 10]
             :major       0.25
             :pos         50})
   :grid   {:attribs      {:stroke "#ddd"}
            :minor-x      true
            :minor-y      false}
   :data   [{:values      (map-indexed #(-> [%1 0]) (repeat n 0))
             :attribs     {:fill "none" :stroke "#ccc"}
             :layout      viz/svg-line-plot}
            {:values      (map-indexed #(-> [%1 %2]) (take n buffer))
             :attribs     {:fill "none" :stroke "#0af"}
             :layout      viz/svg-line-plot}]})

(defn plot-n
  [n buffer width height]
  (->> (plot-data n buffer width height)
       (viz/svg-plot2d-cartesian)
       (svg/svg {:width width :height height})))

(defn plot-buffers
  [n expected buffer]
  (let [width 400 height 400]
    (sab/html
     [:table
      [:tbody
       [:tr
        [:td (plot-n n expected width height)
         [:div {:style {:text-align "center"}} "Expected"]]
        [:td (plot-n n buffer   width height)
         [:div {:style {:text-align "center"}} "Actual"]]]]])))