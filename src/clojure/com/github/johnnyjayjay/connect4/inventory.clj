(ns com.github.johnnyjayjay.connect4.inventory
  (:require [com.github.johnnyjayjay.connect4.util :refer [cond-doto]]
            [com.github.johnnyjayjay.connect4.game :refer [occupied?]])
  (:import (org.bukkit.inventory ItemStack ItemFlag Inventory)
           (org.bukkit Material Bukkit)
           (org.bukkit.enchantments Enchantment)))

(defn item-stack
  "Creates an `ItemStack` from the given specification map."
  [{{:keys [display-name lore flags enchants] :as meta} :meta
    :keys [type durability amount]}]
  (cond-doto (ItemStack. ^Material type)
             meta (.setItemMeta (cond-doto (.getItemMeta (ItemStack. ^Material type))
                                           display-name (.setDisplayName display-name)
                                           lore (.setLore lore)
                                           flags (.addItemFlags (into-array ItemFlag flags))))
             amount (.setAmount (int amount))
             durability (.setDurability (short durability))
             enchants (.addUnsafeEnchantments enchants)))

(def glass-colors
  {:red    14
   :yellow 4
   :green  13
   :blue   11})

(def border-item
  {:type Material/LADDER
   :meta {:display-name "Connect 4"}})

(def drop-item
  {:type       Material/STAINED_GLASS_PANE
   :durability 8
   :meta       {:display-name "Drop a disc"
                :lore         ["Click to drop a disc here"]}})

(defn disc [player color-data]
  {:type       Material/STAINED_GLASS_PANE
   :durability color-data
   :meta       {:display-name player}})

(defn add-glow [item]
  (-> item
      (assoc-in [:meta :enchants Enchantment/SILK_TOUCH] (int 1))
      (update-in [:meta :flags] conj ItemFlag/HIDE_ENCHANTS)))

(defn prepare-items
  "Takes a game and associates relevant `ItemStack` objects for rendering."
  [{:keys [players] :as game}]
  (let [discs (map disc players (vals glass-colors))
        glowing-discs (map add-glow discs)
        item-map (comp (partial zipmap players) (partial map item-stack))]
    (assoc game :discs (item-map discs) :win-discs (item-map glowing-discs)
                :border-item (item-stack border-item) :drop-item (item-stack drop-item))))

(def row-size 9)

(def row-number 6)

(defn create-inventory []
  (Bukkit/createInventory nil (int (* row-size row-number)) "Connect Four (close to quit)"))

(defn position->slot [[y x]]
  (+ (* x row-size) (inc y)))

(defn column-slots [column]
  (take row-number (range column Long/MAX_VALUE row-size)))

(defn render [{:keys [fields discs drop-item border-item]}]
  (let [^"[Ljava.lang.Object;" contents (make-array ItemStack (* row-size row-number))]
    (run! #(aset contents % drop-item) (range 1 (dec row-size)))
    (run! #(aset contents % border-item) (concat (column-slots 0) (column-slots 8)))
    (run! #(aset contents (position->slot (:position %)) (discs (:owner %))) (filter occupied? (flatten fields)))
    contents))

(defn highlight-win-line!
  [{:keys [win-discs]} ^"[Ljava.lang.Object;" contents winner win-line]
  (let [glowing-disc (win-discs winner)]
    (run! #(aset contents (position->slot (:position %)) glowing-disc) win-line)
    contents))
