import { useQuery } from "@tanstack/react-query";
import * as notificationApi from "../api/notificationApi";

export function useUnreadCount(enabled = true) {
  return useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: () => notificationApi.getUnreadCount(),
    enabled,
    refetchInterval: 600000,
    refetchIntervalInBackground: true,
    select: (data) => data?.count ?? 0,
  });
}
