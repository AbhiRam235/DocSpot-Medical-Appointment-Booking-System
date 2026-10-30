import { usePaginatedQuery } from "./usePaginatedQuery";
import * as patientApi from "../api/patientApi";

export function useDoctorSearch(filters, page, size = 10) {
  return usePaginatedQuery({
    queryKey: ["doctor-search", filters],
    queryFn: ({ page: p, size: s }) =>
      patientApi.searchDoctors({ ...filters, page: p, size: s }),
    page,
    size,
  });
}
