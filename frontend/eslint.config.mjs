import next from "eslint-config-next";

/** @type {import("eslint").Linter.Config[]} */
const config = [
  {
    ignores: [".next/**", "node_modules/**", "public/**", "next-env.d.ts"],
  },
  ...next,
];

export default config;
