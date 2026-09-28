import { Route, Routes } from 'react-router-dom'
import { Painel } from './painel/Painel'
import { Totem } from './Totem'

export default function App() {
  return (
    <Routes>
      <Route path="/painel" element={<Painel />} />
      <Route path="*" element={<Totem />} />
    </Routes>
  )
}
