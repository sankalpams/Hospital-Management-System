import { forwardRef, type ComponentPropsWithoutRef, type ElementRef } from 'react';
import * as Primitive from '@radix-ui/react-dropdown-menu';
import { Circle } from 'lucide-react';
import { cn } from './cn';

const Content = forwardRef<
  ElementRef<typeof Primitive.Content>,
  ComponentPropsWithoutRef<typeof Primitive.Content>
>(function Content({ className, sideOffset = 6, ...rest }, ref) {
  return (
    <Primitive.Portal>
      <Primitive.Content
        ref={ref}
        sideOffset={sideOffset}
        className={cn(
          'z-50 min-w-48 rounded-card border border-border bg-surface-raised p-1 text-text shadow-raised',
          className,
        )}
        {...rest}
      />
    </Primitive.Portal>
  );
});

const ITEM =
  'relative flex min-h-11 cursor-pointer select-none items-center gap-2 rounded-control px-3 text-sm outline-none data-[disabled]:pointer-events-none data-[disabled]:opacity-60 data-[highlighted]:bg-primary-subtle data-[highlighted]:text-primary-hover';

const Item = forwardRef<
  ElementRef<typeof Primitive.Item>,
  ComponentPropsWithoutRef<typeof Primitive.Item>
>(function Item({ className, ...rest }, ref) {
  return <Primitive.Item ref={ref} className={cn(ITEM, className)} {...rest} />;
});

const RadioItem = forwardRef<
  ElementRef<typeof Primitive.RadioItem>,
  ComponentPropsWithoutRef<typeof Primitive.RadioItem>
>(function RadioItem({ className, children, ...rest }, ref) {
  return (
    <Primitive.RadioItem ref={ref} className={cn(ITEM, 'pl-8', className)} {...rest}>
      <span className="absolute left-3 flex h-3 w-3 items-center justify-center">
        <Primitive.ItemIndicator>
          <Circle aria-hidden="true" className="h-2 w-2 fill-current" />
        </Primitive.ItemIndicator>
      </span>
      {children}
    </Primitive.RadioItem>
  );
});

const Separator = forwardRef<
  ElementRef<typeof Primitive.Separator>,
  ComponentPropsWithoutRef<typeof Primitive.Separator>
>(function Separator({ className, ...rest }, ref) {
  return <Primitive.Separator ref={ref} className={cn('-mx-1 my-1 h-px bg-border', className)} {...rest} />;
});

const Label = forwardRef<
  ElementRef<typeof Primitive.Label>,
  ComponentPropsWithoutRef<typeof Primitive.Label>
>(function Label({ className, ...rest }, ref) {
  return (
    <Primitive.Label ref={ref} className={cn('px-3 py-1.5 text-xs font-medium text-muted', className)} {...rest} />
  );
});

export const DropdownMenu = {
  Root: Primitive.Root,
  Trigger: Primitive.Trigger,
  Content,
  Item,
  RadioGroup: Primitive.RadioGroup,
  RadioItem,
  Separator,
  Label,
};
