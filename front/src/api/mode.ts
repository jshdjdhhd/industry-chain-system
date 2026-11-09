export function isMockApiEnabled(
  value: string | boolean | undefined = import.meta.env.VITE_API_MOCK,
) {
  if (typeof value === "boolean") return value;
  if (value === undefined || value === "") return false;
  return !["false", "0", "off", "backend"].includes(value.toLowerCase());
}
