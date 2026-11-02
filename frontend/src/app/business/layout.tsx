import type { ReactNode } from "react";
import { OwnerApplicationLayout } from "../../components/layout/OwnerApplicationLayout";

export default function BusinessLayout({ children }: { children: ReactNode }) {
  return <OwnerApplicationLayout>{children}</OwnerApplicationLayout>;
}
