import { useRef, useState, type DragEvent, type ChangeEvent } from "react";
import { UploadCloud, X, FileText } from "lucide-react";

interface FileUploaderProps {
  files: File[];
  onFilesChange: (files: File[]) => void;
}

export default function FileUploader({
  files,
  onFilesChange,
}: FileUploaderProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = useState(false);

  function handleDrop(e: DragEvent) {
    e.preventDefault();
    setIsDragging(false);
    const dropped = Array.from(e.dataTransfer.files);
    onFilesChange([...files, ...dropped]);
  }

  function handleSelect(e: ChangeEvent<HTMLInputElement>) {
    if (e.target.files) {
      onFilesChange([...files, ...Array.from(e.target.files)]);
    }
  }

  function removeFile(index: number) {
    onFilesChange(files.filter((_, i) => i !== index));
  }

  return (
    <div>
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setIsDragging(true);
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
        className={`border-2 border-dashed rounded-xl p-10 flex flex-col items-center justify-center cursor-pointer transition-colors ${
          isDragging ? "border-primary bg-primary/5" : "border-gray-300"
        }`}
      >
        <UploadCloud size={32} className="text-gray-400 mb-2" />
        <p className="text-sm text-gray-500 underline">Drag or drop a file</p>
        <input
          title="File input"
          ref={inputRef}
          type="file"
          multiple
          onChange={handleSelect}
          className="hidden"
        />
      </div>

      {files.length > 0 && (
        <div className="mt-3 space-y-2">
          {files.map((file, index) => (
            <div
              key={index}
              className="flex items-center justify-between px-4 py-2 border border-gray-200 rounded-lg text-sm"
            >
              <div className="flex items-center gap-2 text-gray-700">
                <FileText size={16} />
                {file.name}
              </div>
              <button
                title="close"
                onClick={() => removeFile(index)}
                className="text-gray-400 hover:text-red-500"
              >
                <X size={16} />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
