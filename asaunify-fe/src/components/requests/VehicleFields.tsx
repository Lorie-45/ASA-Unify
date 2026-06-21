interface VehicleFieldsProps {
  destination: string;
  tripDate: string;
  purpose: string;
  onDestinationChange: (value: string) => void;
  onTripDateChange: (value: string) => void;
  onPurposeChange: (value: string) => void;
}

export default function VehicleFields({
  destination,
  tripDate,
  purpose,
  onDestinationChange,
  onTripDateChange,
  onPurposeChange,
}: VehicleFieldsProps) {
  return (
    <div className="grid grid-cols-2 gap-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Destination
        </label>
        <input
          type="text"
          value={destination}
          onChange={(e) => onDestinationChange(e.target.value)}
          placeholder="e.g. Musanze"
          className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Trip Date
        </label>
        <input
          title="Trip date"
          type="date"
          value={tripDate}
          onChange={(e) => onTripDateChange(e.target.value)}
          className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
        />
      </div>
      <div className="col-span-2">
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Purpose of Trip
        </label>
        <input
          type="text"
          value={purpose}
          onChange={(e) => onPurposeChange(e.target.value)}
          placeholder="e.g. Field visit to branch office"
          className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
        />
      </div>
    </div>
  );
}
