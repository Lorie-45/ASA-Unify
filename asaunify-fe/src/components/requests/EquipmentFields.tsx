interface EquipmentFieldsProps {
  itemName: string;
  quantity: number;
  onItemNameChange: (value: string) => void;
  onQuantityChange: (value: number) => void;
}

export default function EquipmentFields({
  itemName,
  quantity,
  onItemNameChange,
  onQuantityChange,
}: EquipmentFieldsProps) {
  return (
    <div className="grid grid-cols-2 gap-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Item Name
        </label>
        <input
          type="text"
          value={itemName}
          onChange={(e) => onItemNameChange(e.target.value)}
          placeholder="e.g. Laptop, Office Chair"
          className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Quantity
        </label>
        <input
          title="Quantity"
          type="number"
          min={1}
          value={quantity}
          onChange={(e) => onQuantityChange(Number(e.target.value))}
          className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
        />
      </div>
    </div>
  );
}
