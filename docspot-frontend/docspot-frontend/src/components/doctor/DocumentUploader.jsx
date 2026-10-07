import { useState } from "react";
import toast from "react-hot-toast";
import { DOCUMENT_TYPE, humanize } from "../../utils/enums";
import * as doctorApi from "../../api/doctorApi";
import Input from "../common/Input";
import Button from "../common/Button";

const MAX_SIZE_MB = 5;

export default function DocumentUploader({ onUploaded }) {
  const [documentType, setDocumentType] = useState(DOCUMENT_TYPE[0]);
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);

  const handleUpload = async () => {
    if (!file) {
      toast.error("Choose a file first.");
      return;
    }
    if (file.size > MAX_SIZE_MB * 1024 * 1024) {
      toast.error(`File must be under ${MAX_SIZE_MB}MB.`);
      return;
    }
    setUploading(true);
    try {
      await doctorApi.uploadDocument(documentType, file);
      toast.success("Document uploaded.");
      setFile(null);
      onUploaded?.();
    } catch (err) {
      toast.error(err.response?.data?.message || "Upload failed.");
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="flex flex-col gap-3 rounded border border-border bg-white p-4 sm:flex-row sm:items-end">
      <Input
        as="select"
        label="Document type"
        value={documentType}
        onChange={(e) => setDocumentType(e.target.value)}
        className="sm:w-56"
      >
        {DOCUMENT_TYPE.map((t) => (
          <option key={t} value={t}>
            {humanize(t)}
          </option>
        ))}
      </Input>
      <label className="block flex-1">
        <span className="mb-1 block text-sm font-medium text-ink-soft">
          File (PDF, JPG or PNG, max {MAX_SIZE_MB}MB)
        </span>
        <input
          type="file"
          accept=".pdf,.jpg,.jpeg,.png"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className="w-full text-sm text-ink-soft file:mr-3 file:rounded file:border-0 file:bg-primary-light file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-primary-dark"
        />
      </label>
      <Button onClick={handleUpload} loading={uploading}>
        Upload
      </Button>
    </div>
  );
}
