(ns com.github.johnnyjayjay.connect4.inventory
  (:require [com.github.johnnyjayjay.connect4.util :refer [cond-doto]])
  (:import (org.bukkit.inventory ItemStack ItemFlag)
           (org.bukkit Material Bukkit)
           (org.bukkit.enchantments Enchantment)))

(defn item-stack
  [{{:keys [display-name lore flags unbreakable? enchants] :as meta} :meta
    :keys                                                            [type durability amount]}]
  (cond-doto (ItemStack. ^Material type)
             amount (.setAmount (int amount))
             durability (.setDurability (short durability))
             meta (.setItemMeta (cond-doto (.getItemMeta (ItemStack. ^Material type))
                                           display-name (.setDisplayName display-name)
                                           lore (.setLore lore)
                                           flags (.addItemFlags (to-array flags))
                                           unbreakable? (.setUnbreakable true)))
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
      (assoc :enchants {Enchantment/SILK_TOUCH 1})
      (assoc-in [:meta :flags] [ItemFlag/HIDE_ENCHANTS])))

(defn prepare-items [{:keys [players] :as game}]
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
  (let [contents (make-array ItemStack (* row-size row-number))]
    (doseq [drop-slot (range 1 (dec row-size))]
      (aset contents drop-slot drop-item))
    (doseq [border-slot (concat (column-slots 0) (column-slots 8))]
      (aset contents border-slot border-item))
    (doseq [{:keys [owner position]} (flatten fields)]
      (aset contents (position->slot position) (discs owner)))
    contents))

(defn highlight-win-line [{:keys [win-discs]} contents winner win-line]
  (let [glowing-disc (win-discs winner)]
    (doseq [{:keys [position]} win-line]
      (aset contents (position->slot position) glowing-disc))))
