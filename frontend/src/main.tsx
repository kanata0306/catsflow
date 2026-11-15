import React from "react";
import { createRoot } from "react-dom/client";
import App from "./CatsflowApp";
import "./design.css";

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);