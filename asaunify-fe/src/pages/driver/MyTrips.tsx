import { useCallback, useEffect, useState } from 'react';
import { Car, MapPin, Calendar, User, Eye } from 'lucide-react';
import { requestsApi } from '../../api/requests.api';
import { formatDate } from '../../utils/formatDate';
import type { VehicleTripAssignmentDto } from '../../types/request.types';

export default function MyTrips() {
  const [trips, setTrips] = useState<VehicleTripAssignmentDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const loadTrips = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await requestsApi.getMyTrips();
      setTrips(data);
    } catch (error) {
      console.error('Failed to load trips:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadTrips();
  }, [loadTrips]);

  async function handleMarkSeen(requestId: string) {
    try {
      await requestsApi.markTripSeen(requestId);
      loadTrips();
    } catch (error) {
      console.error('Failed to mark trip as seen:', error);
    }
  }

  const upcoming = trips.filter((t) => t.seenAt === null);
  const seen = trips.filter((t) => t.seenAt !== null);

  if (isLoading) {
    return (
      <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
    );
  }

  return (
    <div className="space-y-10">
      <h1 className="text-xl font-bold text-gray-900">My Trips</h1>

      {/* New / Unseen trips */}
      <section>
        <h2 className="text-lg font-bold text-teal mb-1">
          New Assignments
        </h2>
        <p className="text-sm text-gray-500 mb-4">
          Trips assigned to you that you haven't acknowledged yet
        </p>
        {upcoming.length === 0 ? (
          <div className="border border-dashed border-gray-200 rounded-xl p-8 text-center text-gray-400 text-sm">
            No new trip assignments
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {upcoming.map((trip) => (
              <TripCard
                key={trip.id}
                trip={trip}
                onMarkSeen={() => handleMarkSeen(trip.requestId)}
              />
            ))}
          </div>
        )}
      </section>

      {/* Previously seen trips */}
      <section>
        <h2 className="text-lg font-bold text-teal mb-1">Past Trips</h2>
        <p className="text-sm text-gray-500 mb-4">
          Trips you have already acknowledged
        </p>
        {seen.length === 0 ? (
          <div className="border border-dashed border-gray-200 rounded-xl p-8 text-center text-gray-400 text-sm">
            No past trips
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {seen.map((trip) => (
              <TripCard key={trip.id} trip={trip} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

// ─── Trip Card ────────────────────────────────────────────

function TripCard({
  trip,
  onMarkSeen,
}: {
  trip: VehicleTripAssignmentDto;
  onMarkSeen?: () => void;
}) {
  return (
    <div
      className={`border rounded-xl p-5 space-y-3 ${
        trip.seenAt === null
          ? 'border-primary/40 bg-primary/5'
          : 'border-gray-200 bg-white'
      }`}
    >
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Car size={18} className="text-teal" />
          <span className="font-semibold text-gray-900 text-sm">
            {trip.requestReferenceNumber}
          </span>
        </div>
        {trip.seenAt === null && (
          <span className="text-xs font-semibold text-primary bg-primary/10 px-2 py-1 rounded-full">
            New
          </span>
        )}
      </div>

      <p className="text-sm font-medium text-gray-800">{trip.requestTitle}</p>

      {/* Trip details */}
      <div className="space-y-2">
        <div className="flex items-center gap-2 text-sm text-gray-600">
          <MapPin size={14} className="text-gray-400 shrink-0" />
          <span>{trip.destination || '—'}</span>
        </div>
        <div className="flex items-center gap-2 text-sm text-gray-600">
          <Calendar size={14} className="text-gray-400 shrink-0" />
          <span>{trip.tripDate || '—'}</span>
        </div>
        <div className="flex items-center gap-2 text-sm text-gray-600">
          <User size={14} className="text-gray-400 shrink-0" />
          <span>Requested by {trip.initiatorName}</span>
        </div>
      </div>

      {/* Note */}
      {trip.note && (
        <div className="bg-gray-50 rounded-lg px-3 py-2">
          <p className="text-xs text-gray-500 font-medium mb-1">
            Note from Fleet Manager
          </p>
          <p className="text-sm text-gray-700">{trip.note}</p>
        </div>
      )}

      {/* Assignment info */}
      <p className="text-xs text-gray-400">
        Assigned by {trip.assignedByName} · {formatDate(trip.assignedAt)}
      </p>

      {/* Acknowledge button */}
      {trip.seenAt === null && onMarkSeen && (
        <button
          onClick={onMarkSeen}
          className="w-full flex items-center justify-center gap-2 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-dark transition-colors"
        >
          <Eye size={16} />
          Acknowledge Trip
        </button>
      )}

      {trip.seenAt && (
        <p className="text-xs text-status-approved font-medium">
          ✓ Acknowledged on {formatDate(trip.seenAt)}
        </p>
      )}
    </div>
  );
}