(ns com.github.johnnyjayjay.connect4.game)

(def generate-fields
  "Given a `width` and a `height`, generates a 2d vector of empty fields."
  (memoize
    (fn [width height]
      (vec (for [y (range width)]
             (vec (for [x (range height)]
                    {:position [y x]})))))))

(defn occupied?
  "Returns whether the given field is occupied."
  [field]
  (contains? field :owner))

(defn make-game
  "Makes a new connect four game.

  A game is a map with the following keys:
  - `:width` the width of the playing field
  - `:height` the height of the playing field
  - `:win-line-length` the number of consecutive fields you need to occupy in order to win
  - `:players` the set of players in the game
  - `:cycle` an infinite sequence defining the order of turns
  - `:fields` a 2d vector representing the columns and rows of the playing field"
  [players & {:keys [width height win-line-length] :or {width 7 height 6 win-line-length 4} :as opts}]
  {:width           width
   :height          height
   :win-line-length win-line-length
   :players         (set players)
   :cycle           (cycle players)
   :fields          (generate-fields width height)})

(defn play
  "Drops a disc for in the given column for the current player and updates the game."
  [{[player] :cycle :as game} column]
  (if-let [[y x] (:position (first (remove occupied? (rseq (get-in game [:fields column])))))]
    (-> game
        (update-in [:fields y x] assoc :owner player)
        (update :cycle rest)
        (assoc :last-player player))
    game))

(defn in-bounds?
  "Returns whether the given `[y x]` position is in bounds of the given `width` and `height`."
  [width height [y x]]
  (and (< -1 y width) (< -1 x height)))

(defn direction
  "Returns a function that modifies a `[y x]` position according to the given modifiers."
  [y-modifier x-modifier]
  (fn [[y x]]
    [(y-modifier y) (x-modifier x)]))

(def up-left->down-right
  "A pair of functions that move a `[y x]` position along the up-left to down-right diagonal of a game."
  [(direction inc inc) (direction dec dec)])

(def up-right->down-left
  "A pair of functions that move a `[y x]` position along the up-right to down-left diagonal of a game."
  [(direction dec inc) (direction inc dec)])

(defn diagonal-lines
  "Returns all diagonal lines given the `fields`, `width` and `height` of the game as well as a direction ([[up-right->down-left]] or [[up-left->down-right]])."
  [fields width height [move-down move-up :as _direction]]
  (for [y (range width)
        x (range height)
        :let [coordinates [y x]]
        :when (not (in-bounds? width height (move-up coordinates)))]
    (->> coordinates
         (iterate move-down)
         (take-while (partial in-bounds? width height))
         (map (partial get-in fields)))))

(defn columns
  "Returns the columns in this game."
  [{:keys [fields]}] fields)

(defn rows
  "Returns the rows in this game."
  [{:keys [height fields]}]
  (map (fn [row] (map get fields (repeat row))) (range height)))

(defn diagonals
  "Returns the diagonals in this game."
  [{:keys [width height fields]}]
  (concat (diagonal-lines fields width height up-left->down-right)
          (diagonal-lines fields width height up-right->down-left)))

(defn valid-columns
  "Returns the set of columns where discs can still be dropped in this game."
  [game]
  (->> (columns game)
       (map first)
       (remove occupied?)
       (map :position)
       (map first)
       (set)))

(defn tied?
  "Returns whether this game is a tie (no more moves can be made)."
  [game]
  (every? occupied? (map first (columns game))))

(defn win-line
  "Given a `win-line-length` and a `line` of fields, returns the consecutive fields in the line that comprise a winning line or `nil` if none exists."
  [win-line-length line]
  (->> line
       (partition-by :owner)
       (filter (partial every? occupied?))
       (filter (comp #{win-line-length} count))
       first))

(defn find-win-line
  "Given a sequence of `lines` of fields, returns the first winning line according to the given `win-line-length` or `nil` if none exists."
  [win-line-length lines]
  (first (keep (partial win-line win-line-length) lines)))

(defn win
  "Returns a sequence of fields that comprise the winning line in a game, or `nil` if the game has not been won yet."
  [{:keys [win-line-length] :as game}]
  (some (partial find-win-line win-line-length) (map #(% game) [columns rows diagonals])))

(defn assess
  "Returns information about the game's state.

  This information is a map with a `:state` key that is one of the following:
  - `:tied` no further moves are possible
  - `:won` a player (`:winner`) has won the game with their constructed line (`:win-line`)
  - `:continue` game is still in progress"
  [game]
  (if (tied? game)
    {:state :tied}
    (if-let [win (win game)]
      {:state    :won
       :win-line win
       :winner   (:last-player game)}
      {:state :continue})))
