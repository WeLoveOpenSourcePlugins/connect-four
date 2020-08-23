(ns com.github.johnnyjayjay.connect4.game)

(def generate-fields
  (memoize
    (fn [width height]
      (vec (for [y (range width)]
             (vec (for [x (range height)]
                    {:position [y x]})))))))

(defn occupied? [field]
  (contains? field :owner))

(defn start-game
  [players & {:keys [width height win-line-length] :or {width 7 height 6 win-line-length 4} :as opts}]
  {:width           width
   :height          height
   :win-line-length win-line-length
   :players         (set players)
   :cycle           (cycle players)
   :fields          (generate-fields width height)})

(defn play
  [{[player] :cycle :as game} column]
  (if-let [[y x] (:position (first (remove occupied? (rseq (get-in game [:fields column])))))]
    (-> game
        (update-in [:fields y x] assoc :owner player)
        (update :cycle rest)
        (assoc :last-player player))
    game))

(defn valid-columns [{:keys [fields]}]
  (->> fields
       (map first)
       (remove occupied?)
       (map :position)
       (map first)
       (set)))

(defn tied? [{:keys [fields]}]
  (every? occupied? (flatten fields)))

(defn win-line [win-line-length line]
  (->> line
       (partition-by :owner)
       (filter (partial every? occupied?))
       (filter (comp #{win-line-length} count))
       first))

(defn find-win-line [win-line-length lines]
  (first (keep (partial win-line win-line-length) lines)))

(defn vertical-win [{:keys [fields win-line-length]}]
  (find-win-line win-line-length fields))

(defn horizontal-win [{:keys [fields win-line-length height]}]
  (find-win-line win-line-length (map #(map get % (range height)) fields)))

(defn in-bounds? [width height [y x]]
  (and (< -1 y width) (< -1 x height)))

(defn direction [y-modifier x-modifier]
  (fn [[y x]]
    [(y-modifier y) (x-modifier x)]))

(def up-left->down-right
  [(direction inc inc) (direction dec dec)])

(def up-right->down-left
  [(direction dec inc) (direction inc dec)])

(defn diagonal-lines [fields width height [move-down move-up :as _direction]]
  (for [y (range width)
        x (range height)
        :let [coordinates [y x]]
        :when (not (in-bounds? width height (move-up coordinates)))]
    (->> coordinates
         (iterate move-down)
         (take-while (partial in-bounds? width height))
         (map (partial get-in fields)))))

(defn diagonal-win [{:keys [fields win-line-length width height]}]
  (find-win-line
    win-line-length
    (concat (diagonal-lines fields width height up-left->down-right)
            (diagonal-lines fields width height up-right->down-left))))

(defn win [game]
  (or (vertical-win game)
      (horizontal-win game)
      (diagonal-win game)))

(defn assess [game]
  (if (tied? game)
    {:state :tied}
    (if-let [win (win game)]
      {:state    :won
       :win-line win
       :player   (:last-player game)}
      {:state :continue})))
