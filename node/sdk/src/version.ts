// SDK semantic version. Bumped in lockstep with all other SDKs by the
// release.yaml workflow.
export const SDK_VERSION = "1.2.2";

// The version the SDK reports, in the health-check headers and the validation log: the override,
// unless it is missing or blank, as in every SDK.
export function reportedVersion(version?: string | null): string {
  return typeof version === "string" && version.trim() !== "" ? version : SDK_VERSION;
}
