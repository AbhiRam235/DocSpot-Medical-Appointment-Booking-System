import { useQuery, useQueryClient } from "@tanstack/react-query";
import * as doctorApi from "../../api/doctorApi";
import { humanize } from "../../utils/enums";
import DocumentUploader from "../../components/doctor/DocumentUploader";
import LoadingSpinner from "../../components/common/LoadingSpinner";

export default function DoctorDocumentsPage() {
  const queryClient = useQueryClient();
  const { data: documents = [], isLoading } = useQuery({
    queryKey: ["doctor-documents"],
    queryFn: doctorApi.getDocuments,
  });

  return (
    <div className="max-w-2xl">
      <h1 className="font-display text-2xl font-medium text-ink">Verification documents</h1>
      <p className="mt-1 text-sm text-ink-faint">
        Upload your credentials for admin review — your license, degree certificate, and ID.
      </p>

      <div className="mt-5">
        <DocumentUploader
          onUploaded={() => queryClient.invalidateQueries({ queryKey: ["doctor-documents"] })}
        />
      </div>

      <div className="mt-5 space-y-2">
        {isLoading ? (
          <LoadingSpinner />
        ) : documents.length === 0 ? (
          <p className="text-sm text-ink-faint">No documents uploaded yet.</p>
        ) : (
          documents.map((doc) => (
            <div
              key={doc.documentId}
              className="flex items-center justify-between rounded border border-border bg-white px-4 py-3"
            >
              <div>
                <p className="text-sm font-medium text-ink">{humanize(doc.documentType)}</p>
                <p className="text-xs text-ink-faint">{doc.fileName}</p>
              </div>
              <a
                href={doc.fileUrl}
                target="_blank"
                rel="noreferrer"
                className="text-sm font-medium text-primary hover:underline"
              >
                View
              </a>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
