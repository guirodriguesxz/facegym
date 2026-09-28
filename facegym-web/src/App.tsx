import { Route, Routes } from 'react-router-dom'
import { Totem } from './Totem'

export default function App() {
  return (
    <Routes>
      <Route path="*" element={<Totem />} />
    </Routes>
  )
}
