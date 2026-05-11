import React, { useState, useEffect, ChangeEvent, memo } from "react";
import { FileUploader } from "@carbon/react";
import { FormattedMessage } from "react-intl";
import { toBase64 } from "../../utils/Utils";

interface ResultFile {
  fileName: string;
  fileType: string;
  base64Content: string;
}

interface TestResultItem {
  accessionNumber: string;
  isModified?: boolean;
  resultFile?: ResultFile | null;
  [key: string]: any;
}

interface Results {
  testResult: TestResultItem[];
}

interface CompactFileInputProps {
  data: {
    id: string;
    accessionNumber: string;
  };
  results: Results;
  setResultForm: (updated: Results) => void;
}

const CompactFileInput: React.FC<CompactFileInputProps> = memo(
  ({ data, results, setResultForm }) => {
    const [uploadedFile, setUploadedFile] = useState<
      Record<string, ResultFile | null>
    >({});

    const rowKey = data.id;
    const currentFile = uploadedFile[rowKey];

    const handleUpload = async (
      e: ChangeEvent<HTMLInputElement>,
    ): Promise<void> => {
      const file = e.target.files?.[0];
      if (!file) return;

      try {
        const base64Content = (await toBase64(file)) as string;
        const updatedResults = structuredClone(results) as Results;

        const itemIndex = updatedResults.testResult.findIndex(
          (item) => String(item.id) === String(rowKey),
        );

        if (itemIndex === -1) {
          console.warn(
            `[CompactFileInput] Row "${rowKey}" not found in results array.`,
          );
          return;
        }

        updatedResults.testResult[itemIndex] = {
          ...updatedResults.testResult[itemIndex],
          isModified: true,
          resultFile: {
            fileName: file.name,
            fileType: file.type,
            base64Content,
          },
        };

        setResultForm(updatedResults);

        setUploadedFile((prev) => ({
          ...prev,
          [rowKey]: updatedResults.testResult[itemIndex].resultFile,
        }));
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : String(err);
        console.error(
          `[CompactFileInput] Failed to convert file to base64: ${errorMessage}`,
        );
      }
    };

    useEffect(() => {
      const testResultItem = results.testResult.find(
        (item) => String(item.id) === String(rowKey),
      );

      setUploadedFile((prev) => ({
        ...prev,
        [rowKey]: testResultItem?.resultFile ?? null,
      }));
    }, [results, rowKey]);

    return (
      <FileUploader
        buttonLabel={<FormattedMessage id="label.button.uploadfile" />}
        filenameStatus={currentFile ? "complete" : ""}
        accept={["image/jpeg", "image/png", "application/pdf"]}
        multiple={false}
        onChange={handleUpload}
        filename={currentFile?.fileName}
      />
    );
  },
);

export default CompactFileInput;
