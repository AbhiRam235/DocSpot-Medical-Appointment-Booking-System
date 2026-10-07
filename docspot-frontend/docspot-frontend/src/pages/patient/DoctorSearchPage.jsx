import { useState } from "react";
import { useDoctorSearch } from "../../hooks/useDoctors";
import { SPECIALITY, GENDER, humanize } from "../../utils/enums";
import DoctorCard from "../../components/doctor/DoctorCard";
import Input from "../../components/common/Input";
import Pagination from "../../components/common/Pagination";
import LoadingSpinner from "../../components/common/LoadingSpinner";

const EMPTY_FILTERS = {
  speciality: "",
  gender: "",
  city: "",
  minExperience: "",
  maxExperience: "",
  minFee: "",
  maxFee: "",
  sortBy: "",
  sortOrder: "asc",
};

export default function DoctorSearchPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(0);

  const activeFilters = Object.fromEntries(
    Object.entries(filters).filter(([, v]) => v !== "")
  );

  const { content: doctors, totalPages, isFirst, isLast, isLoading } = useDoctorSearch(
    activeFilters,
    page
  );

  const update = (key) => (e) => {
    setPage(0);
    setFilters((f) => ({ ...f, [key]: e.target.value }));
  };

  return (
    <div>
      <h1 className="font-display text-2xl font-medium text-ink">Find a doctor</h1>
      <div className="mt-4 grid grid-cols-2 gap-3 rounded border border-border bg-white p-4 sm:grid-cols-4">
        <Input as="select" label="Speciality" value={filters.speciality} onChange={update("speciality")}>
          <option value="">Any</option>
          {SPECIALITY.map((s) => (
            <option key={s} value={s}>
              {humanize(s)}
            </option>
          ))}
        </Input>
        <Input as="select" label="Gender" value={filters.gender} onChange={update("gender")}>
          <option value="">Any</option>
          {GENDER.map((g) => (
            <option key={g} value={g}>
              {humanize(g)}
            </option>
          ))}
        </Input>
        <Input label="City" value={filters.city} onChange={update("city")} />
        <Input
          as="select"
          label="Sort by"
          value={filters.sortBy}
          onChange={update("sortBy")}
        >
          <option value="">Relevance</option>
          <option value="fee">Fee</option>
          <option value="experience">Experience</option>
        </Input>
        <Input label="Min experience (yrs)" type="number" value={filters.minExperience} onChange={update("minExperience")} />
        <Input label="Max experience (yrs)" type="number" value={filters.maxExperience} onChange={update("maxExperience")} />
        <Input label="Min fee" type="number" value={filters.minFee} onChange={update("minFee")} />
        <Input label="Max fee" type="number" value={filters.maxFee} onChange={update("maxFee")} />
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : doctors.length === 0 ? (
        <p className="mt-8 text-center text-sm text-ink-faint">
          No doctors match those filters. Try widening your search.
        </p>
      ) : (
        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
          {doctors.map((d) => (
            <DoctorCard key={d.doctorId} doctor={d} />
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} isFirst={isFirst} isLast={isLast} onPageChange={setPage} />
    </div>
  );
}
