// Plan-3 Task 8: type declaration for Vite's ?raw string loader.
// Allows TypeScript to resolve `import foo from "...?raw"` as string.
declare module "*.yaml?raw" {
  const content: string;
  export default content;
}
declare module "*.yml?raw" {
  const content: string;
  export default content;
}
