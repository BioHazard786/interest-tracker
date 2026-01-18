import { type Config } from "prettier";

const config: Config = {
  semi: false,
  singleQuote: false,
  trailingComma: "all",
  arrowParens: "avoid",
  printWidth: 100,
  tabWidth: 2,
  plugins: ["prettier-plugin-organize-imports", "prettier-plugin-tailwindcss"],
  tailwindStylesheet: "./src/app/globals.css",
};

export default config;
