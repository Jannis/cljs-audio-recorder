(ns audio-utils.gate
  (:require [audio-utils.ring-buffer :as rb]
            [audio-utils.rms-buffer :as rms]
            [audio-utils.util :refer [aatom << >> aswap!
                                      db->amplitude time->samples]]
            [audio-utils.worker :as w]))

(defprotocol IGate
  (generate-output-sample [this channel input-sample])
  (add-to-rms [this channel sample])
  (calculate-rms [this channel])
  (queue-in-buffer [this channel sample])
  (dequeue-from-buffer [this channel]))

(defrecord Gate [config next buffers rms-buffers samples-held]
  w/IWorkerAudioNode
  (connect [this destination]
    (reset! next destination))

  (disconnect [this]
    (reset! next nil))

  (process-audio [this data]
    (let [n-channels (count data)
          n-samples  (count (first data))
          output     (volatile! (into [] (repeat n-channels [])))]
      (dotimes [n n-samples]
        (dotimes [channel n-channels]
          (let [input-sample  ((data channel) n)
                output-sample (generate-output-sample this channel
                                                      input-sample)]
            (vswap! output update channel conj output-sample))))
      (some-> @next (w/process-audio @output))))

  IGate
  (generate-output-sample [this channel input-sample]
    (queue-in-buffer this channel input-sample)
    (add-to-rms this channel input-sample)
    (let [threshold (db->amplitude (:threshold config))
          hold      (time->samples (:hold config) (:sample-rate config))]
      (if (>= (calculate-rms this channel) threshold)
        (do
          (>> samples-held 0)
          (dequeue-from-buffer this channel))
        (if (<= (<< samples-held) hold)
          (do
            (aswap! samples-held inc)
            input-sample)
          0.0))))

  (add-to-rms [this channel sample]
    (aswap! rms-buffers update channel
            (fn [buffer]
              (let [window (max 2 (time->samples (:rms-window config)
                                                 (:sample-rate config)))
                    buffer (or buffer (rms/rms-buffer window))]
                (rms/rms-push buffer sample)))))

  (calculate-rms [this channel]
    (rms/rms ((<< rms-buffers) channel)))

  (queue-in-buffer [this channel sample]
    (aswap! buffers update channel
            (fn [buffer]
              (let [size   (max 2 (time->samples (:look-ahead config)
                                                 (:sample-rate config)))
                    buffer (or buffer (rb/ring-buffer size))]
                (conj buffer sample)))))

  (dequeue-from-buffer [this channel]
    (let [buffer ((<< buffers) channel)
          sample (peek buffer)]
      (pop buffer)
      sample)))

(defn default-trigger
  [gate state])

(defn gate
  [{:keys [sample-rate
           buffer-size
           threshold
           look-ahead
           hold
           rms-window
           trigger]
    :or   {buffer-size 4096
           threshold   -32.0
           look-ahead  500
           hold        100
           rms-window  100
           trigger     default-trigger}}]
  (let [config {:sample-rate  sample-rate
                :threshold    threshold
                :look-ahead   look-ahead
                :hold         hold
                :rms-window   rms-window
                :trigger      trigger}]
    (map->Gate {:config       config
                :next         (atom nil)
                :buffers      (aatom {})
                :rms-buffers  (aatom {})
                :samples-held (aatom 0)})))
