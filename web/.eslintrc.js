module.exports = {
  root: true,
  env: {
    node: true,
    browser: true,
    es2022: true
  },
  extends: [
    // vue3-essential 是 Vue 3 对应的规则集，原先误用了 Vue 2 的 vue/essential
    'plugin:vue/vue3-essential',
    'eslint:recommended'
  ],
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module'
  },
  rules: {
    // 存量代码中有大量 console.error，先降级为警告，逐步清理后再收紧为 error
    'no-console': ['warn', { allow: ['warn', 'error'] }],
    'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'warn',
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    eqeqeq: ['warn', 'smart'],
    'no-var': 'error',
    'prefer-const': 'warn',
    'vue/multi-word-component-names': 'off'
  },
  overrides: [
    {
      files: ['**/*.test.js', '**/__tests__/**/*.js'],
      env: { node: true },
      globals: {
        describe: 'readonly',
        it: 'readonly',
        expect: 'readonly',
        vi: 'readonly',
        beforeEach: 'readonly',
        afterEach: 'readonly'
      }
    }
  ]
}
