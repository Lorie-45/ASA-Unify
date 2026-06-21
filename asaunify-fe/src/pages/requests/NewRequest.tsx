import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Check, Send } from "lucide-react";
import { requestsApi } from "../../api/requests.api";
import { RequestType } from "../../types/enums";
import type { CreateRequestDto } from "../../types/request.types";
import Button from "../../components/ui/Button";
import VehicleFields from "../../components/requests/VehicleFields";
import EquipmentFields from "../../components/requests/EquipmentFields";
import LoanFields from "../../components/requests/LoanFields";
import FileUploader from "../../components/ui/FileUploader";

export default function NewRequest() {
  const navigate = useNavigate();

  const [type, setType] = useState<RequestType | "">("");
  const [title, setTitle] = useState("");
  const [details, setDetails] = useState("");
  const [notes, setNotes] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Equipment fields
  const [itemName, setItemName] = useState("");
  const [quantity, setQuantity] = useState(1);

  // Vehicle fields
  const [destination, setDestination] = useState("");
  const [tripDate, setTripDate] = useState("");
  const [purpose, setPurpose] = useState("");

  // Loan fields
  const [amount, setAmount] = useState(0);
  const [isTopUp, setIsTopUp] = useState(false);
  const [parentRequestId, setParentRequestId] = useState("");

  function buildExtraFields(): Record<string, unknown> {
    if (type === RequestType.EQUIPMENT) {
      return { item_name: itemName, quantity };
    }
    if (type === RequestType.VEHICLE) {
      return { destination, trip_date: tripDate, purpose };
    }
    if (type === RequestType.LOAN) {
      return { amount, is_top_up: isTopUp };
    }
    return {};
  }

  function validate(): string | null {
    if (!type) return "Please select a request type";
    if (!title.trim()) return "Please enter a title";
    if (!details.trim()) return "Please enter request details";

    if (type === RequestType.EQUIPMENT && !itemName.trim()) {
      return "Please enter the item name";
    }
    if (type === RequestType.VEHICLE && (!destination.trim() || !tripDate)) {
      return "Please fill in destination and trip date";
    }
    if (type === RequestType.LOAN && amount <= 0) {
      return "Please enter a valid loan amount";
    }
    return null;
  }

  async function handleSave(submit: boolean) {
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    setIsSubmitting(true);

    try {
      const dto: CreateRequestDto = {
        type: type as RequestType,
        title,
        details,
        notes: notes || undefined,
        extraFields: buildExtraFields(),
        parentRequestId:
          type === RequestType.LOAN && isTopUp && parentRequestId
            ? parentRequestId
            : undefined,
      };

      const created = await requestsApi.createDraft(dto);

      if (submit) {
        await requestsApi.submitRequest(created.id);
      }

      navigate("/requests");
    } catch (err) {
      console.error("Failed to save request:", err);
      setError("Something went wrong while saving your request.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="max-w-4xl">
      <div className="flex items-center justify-between mb-8">
        <div>
          <p className="text-xs text-gray-400">CN-NEW</p>
          <h1 className="text-xl font-bold text-gray-900">New Request</h1>
        </div>
        <div className="flex gap-3">
          <Button
            variant="outline"
            icon={<Check size={16} />}
            disabled={isSubmitting}
            onClick={() => handleSave(false)}
          >
            Save Draft
          </Button>
          <Button
            icon={<Send size={16} />}
            disabled={isSubmitting}
            onClick={() => handleSave(true)}
          >
            Send
          </Button>
        </div>
      </div>

      {error && (
        <div className="mb-6 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
          {error}
        </div>
      )}

      <div className="space-y-6">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Request Type
            </label>
            <select
              title="Request Type"
              value={type}
              onChange={(e) => setType(e.target.value as RequestType)}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="">Select type</option>
              <option value={RequestType.EQUIPMENT}>Equipment</option>
              <option value={RequestType.VEHICLE}>Vehicle</option>
              <option value={RequestType.LOAN}>Loan</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Title
            </label>
            <input
              title="Title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
        </div>

        {/* Type-specific fields appear here once a type is picked */}
        {type === RequestType.EQUIPMENT && (
          <EquipmentFields
            itemName={itemName}
            quantity={quantity}
            onItemNameChange={setItemName}
            onQuantityChange={setQuantity}
          />
        )}
        {type === RequestType.VEHICLE && (
          <VehicleFields
            destination={destination}
            tripDate={tripDate}
            purpose={purpose}
            onDestinationChange={setDestination}
            onTripDateChange={setTripDate}
            onPurposeChange={setPurpose}
          />
        )}
        {type === RequestType.LOAN && (
          <LoanFields
            amount={amount}
            isTopUp={isTopUp}
            parentRequestId={parentRequestId}
            onAmountChange={setAmount}
            onIsTopUpChange={setIsTopUp}
            onParentRequestIdChange={setParentRequestId}
          />
        )}

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Request Details
          </label>
          <textarea
            placeholder="Write here ..."
            value={details}
            onChange={(e) => setDetails(e.target.value)}
            rows={5}
            className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Notes
          </label>
          <textarea
            placeholder="Write here ..."
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={3}
            className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            Additional Information
          </label>
          <FileUploader files={files} onFilesChange={setFiles} />
        </div>
      </div>
    </div>
  );
}
