import js from "@eslint/js";
import tseslint from "typescript-eslint";
import angular from "@angular-eslint/eslint-plugin";
import angularTemplate from "@angular-eslint/eslint-plugin-template";
import tsParser from "@typescript-eslint/parser";

export default [
    {
        ignores: ["**/dist/**", "**/node_modules/**"],
    },

    js.configs.recommended,

    {
        files: ["**/*.ts"],
        languageOptions: {
            parser: tsParser,
            parserOptions: {
                project: ["./token-management/tsconfig.json"],
                tsconfigRootDir: import.meta.dirname,
            },
            globals: {
                console: "readonly",
                window: "readonly",
                document: "readonly",
                setTimeout: "readonly",
                atob: "readonly"
            },
        },
        plugins: {
            "@typescript-eslint": tseslint.plugin,
            "@angular-eslint": angular,
        },
        rules: {
            "@typescript-eslint/naming-convention": [
                "error",
                {selector: "class", format: ["PascalCase"]},
            ],

            curly: "error",
            eqeqeq: ["error", "always", {null: "ignore"}],
            "guard-for-in": "error",
            "max-len": ["error", {code: 140}],
            "no-bitwise": "error",
            "no-caller": "error",
            "no-console": "off",
            "no-debugger": "error",
            "no-empty": "error",
            "no-eval": "error",
            "no-fallthrough": "error",
            "no-trailing-spaces": "error",
            "no-unused-expressions": "error",
            "no-unused-labels": "error",
            "no-var": "error",
            quotes: ["error", "single"],
            radix: "error",
            semi: ["error", "always"],
            "spaced-comment": "error",
            "brace-style": ["error", "1tbs", {allowSingleLine: true}],

            "@angular-eslint/directive-selector": [
                "error",
                {type: "attribute", prefix: "app", style: "camelCase"},
            ],
            "@angular-eslint/component-selector": [
                "error",
                {type: "element", prefix: "app", style: "kebab-case"},
            ],
            "@angular-eslint/no-output-rename": "error",
            "@angular-eslint/use-pipe-transform-interface": "error",
            "@angular-eslint/component-class-suffix": "error",
            "@angular-eslint/directive-class-suffix": "error",
        },
    },
];