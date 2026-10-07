import { useQuery, useQueryClient } from "@tanstack/react-query";
import * as notificationApi from "../../api/notificationApi";
import LoadingSpinner from "../common/LoadingSpinner";
import { format, parseISO } from "date-fns";

export default function NotificationList() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ["notifications", "list"],
    queryFn: () => notificationApi.getNotifications({ page: 0, size: 20 }),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["notifications"] });
  };

  const handleRead = async (id) => {
    await notificationApi.markAsRead(id);
    invalidate();
  };

  const handleReadAll = async () => {
    await notificationApi.markAllAsRead();
    invalidate();
  };

  const items = data?.content ?? [];

  return (
    <div>
      <div className="flex items-center justify-between border-b border-border px-4 py-3">
        <span className="text-sm font-semibold text-ink">Notifications</span>
        <button onClick={handleReadAll} className="text-xs font-medium text-primary hover:underline">
          Mark all read
        </button>
      </div>
      <div className="max-h-80 overflow-y-auto">
        {isLoading && <LoadingSpinner />}
        {!isLoading && items.length === 0 && (
          <p className="px-4 py-6 text-center text-sm text-ink-faint">
            Nothing here yet.
          </p>
        )}
        {items.map((n) => (
          <button
            key={n.notificationId}
            onClick={() => !n.read && handleRead(n.notificationId)}
            className={`block w-full border-b border-border px-4 py-3 text-left text-sm last:border-b-0 hover:bg-paper ${
              n.read ? "text-ink-faint" : "text-ink"
            }`}
          >
            <div className="flex items-start justify-between gap-2">
              <span className="font-medium">{n.title}</span>
              {!n.read && <span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-accent" />}
            </div>
            <p className="mt-0.5 text-xs">{n.message}</p>
            <p className="mt-1 text-[11px] text-ink-faint">
              {format(parseISO(n.createdAt), "d MMM, h:mm a")}
            </p>
          </button>
        ))}
      </div>
    </div>
  );
}
