# facegym-web

Front do FaceGym em React + TypeScript (Vite, Tailwind).

- **Totem (`/`)**: simula o terminal de reconhecimento facial de uma catraca. Clique num aluno de
  demonstração ou use "Teste com você" para cadastrar o próprio rosto na recepção e passar pela
  catraca com a câmera. Sem reconhecimento, a catraca pede o CPF no teclado.
- **Painel (`/painel`)**: acessos, alunos (bloqueio e remoção de biometria) e planos.

```bash
cp .env.example .env   # VITE_API_URL, padrão http://localhost:8081/api/v1
npm i
npm run dev
npm test
```

Veja o [README da raiz](../README.md) para a arquitetura e para subir a API e a biometria.
